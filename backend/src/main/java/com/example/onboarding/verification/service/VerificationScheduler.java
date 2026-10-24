package com.example.onboarding.verification.service;

import com.example.onboarding.verification.data.VerificationWorkAvailable;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Database-driven, on-demand dispatcher. The timer is a single coordination thread;
 * it NEVER makes external requests. No recurring polling occurs when the DB is idle.
 * Claimed work runs on a bounded worker pool, with PostgreSQL as the durable queue.
 */
@Component
public class VerificationScheduler {
    private static final Logger log = LoggerFactory.getLogger(VerificationScheduler.class);
    private final ThreadPoolTaskScheduler timer;
    private final ThreadPoolTaskExecutor executor;
    private final VerificationJobManager jobs;
    private final VerificationWorker worker;
    private final int concurrency;
    private final AtomicInteger running = new AtomicInteger();

    // Only accessed on the one timer thread.
    private ScheduledFuture<?> nextWake;

    public VerificationScheduler(
            @Qualifier("verificationTimer") ThreadPoolTaskScheduler timer,
            @Qualifier("verificationExecutor") ThreadPoolTaskExecutor executor,
            VerificationJobManager jobs,
            VerificationWorker worker,
            @Value("${app.worker.concurrency:3}") int concurrency) {
        this.timer = timer;
        this.executor = executor;
        this.jobs = jobs;
        this.worker = worker;
        this.concurrency = concurrency;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverAtStartup() {
        // This just queues a DB inspection on the timer thread; boot is not blocked
        // by a Hunter call. Pending jobs and expired leases are eligible for recovery.
        log.info("Recovering unfinished verification jobs after startup");
        wake();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJobCommitted(VerificationWorkAvailable event) {
        wake();
    }

    /** May be called by a request thread or a worker; never runs DB/provider work inline. */
    public void wake() {
        try {
            timer.execute(this::reconcileSafely);
        } catch (RuntimeException exception) {
            // A crash/shutdown after job commit is recovered on next application startup.
            log.warn("Could not signal verification dispatcher: {}", exception.getClass().getSimpleName());
        }
    }

    private void reconcileSafely() {
        try {
            reconcile();
        } catch (RuntimeException exception) {
            log.error("Verification dispatch failed; unfinished jobs remain in PostgreSQL", exception);
            // Avoid a tight loop on persistent database errors.
            cancelTimer();
            nextWake = timer.schedule(this::reconcileSafely, Instant.now().plusSeconds(10));
        }
    }

    private void reconcile() {
        cancelTimer();
        int free = concurrency - running.get();

        if (free > 0) {
            // Read more candidates than slots to account for concurrent claims by
            // other app instances. The DB conditional UPDATE decides the winner.
            for (UUID id : jobs.dueJobIds(free * 4)) {
                if (running.get() >= concurrency) break;
                VerificationJobManager.ClaimedJob claimed = jobs.claim(id);
                if (claimed == null) continue;

                running.incrementAndGet();
                try {
                    executor.execute(() -> {
                        try {
                            worker.processClaimed(claimed);
                        } catch (RuntimeException exception) {
                            // Leave the leased job in PostgreSQL. A future lease-expiry
                            // wake (or startup) can reclaim it after a process failure.
                            log.error("Verification job could not finish: {}", claimed.jobId(), exception);
                        } finally {
                            running.decrementAndGet();
                            wake(); // Release slot, then look for more due work.
                        }
                    });
                } catch (RuntimeException rejected) {
                    running.decrementAndGet();
                    log.error("Worker rejected job {}; lease will expire", claimed.jobId(), rejected);
                }
            }
        }

        if (running.get() >= concurrency) {
            // All slots occupied: completed workers wake the dispatcher; do not
            // repeatedly scan due jobs while threads are busy.
            return;
        }

        Optional<Instant> dueAt = jobs.nextEligibleAt();
        if (dueAt.isEmpty()) {
            log.debug("No unfinished jobs; dispatcher idle, no timer scheduled");
            return;
        }

        Instant now = Instant.now();
        // Any overdue job we could not claim is rechecked in a second, not a busy loop.
        Instant when = dueAt.get().isAfter(now) ? dueAt.get() : now.plusSeconds(1);
        nextWake = timer.schedule(this::reconcileSafely, when);
    }

    private void cancelTimer() {
        if (nextWake != null) {
            nextWake.cancel(false);
            nextWake = null;
        }
    }
}
