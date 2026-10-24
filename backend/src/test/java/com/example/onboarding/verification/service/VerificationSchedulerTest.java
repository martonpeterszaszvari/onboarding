package com.example.onboarding.verification.service;

import com.example.onboarding.verification.data.VerificationWorkAvailable;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/** Unit-level dispatch tests. Lease/database integration needs a real PostgreSQL test. */
class VerificationSchedulerTest {
    private ThreadPoolTaskScheduler timer;
    private ThreadPoolTaskExecutor executor;
    private VerificationJobManager jobs;
    private VerificationWorker worker;
    private VerificationScheduler scheduler;

    @BeforeEach
    void setup() {
        timer = new ThreadPoolTaskScheduler();
        timer.setPoolSize(1);
        timer.initialize();
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("verification-worker-");
        executor.initialize();
        jobs = mock(VerificationJobManager.class);
        worker = mock(VerificationWorker.class);
        scheduler = new VerificationScheduler(timer, executor, jobs, worker, 2);
    }

    @AfterEach
    void cleanup() {
        executor.shutdown();
        timer.shutdown();
    }

    @Test
    void startupRecoversDueWorkOnABackgroundThread() throws Exception {
        UUID id = UUID.randomUUID();
        var claim = new VerificationJobManager.ClaimedJob(id, UUID.randomUUID(), "delay@acme.com", 2);
        CountDownLatch completed = new CountDownLatch(1);
        when(jobs.dueJobIds(anyInt())).thenReturn(List.of(id), List.of());
        when(jobs.claim(id)).thenReturn(claim);
        when(jobs.nextEligibleAt()).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            assertTrue(Thread.currentThread().getName().startsWith("verification"));
            completed.countDown();
            return null;
        }).when(worker).processClaimed(claim);

        scheduler.recoverAtStartup();

        assertTrue(completed.await(3, TimeUnit.SECONDS));
        verify(jobs).claim(id);
        verify(worker).processClaimed(claim);
    }

    @Test
    void newWorkNotificationTriggersDispatchWithoutAPollingTick() throws Exception {
        UUID id = UUID.randomUUID();
        var claim = new VerificationJobManager.ClaimedJob(id, UUID.randomUUID(), "success@acme.com", 1);
        CountDownLatch completed = new CountDownLatch(1);
        when(jobs.dueJobIds(anyInt())).thenReturn(List.of(id), List.of());
        when(jobs.claim(id)).thenReturn(claim);
        when(jobs.nextEligibleAt()).thenReturn(Optional.empty());
        doAnswer(invocation -> { completed.countDown(); return null; }).when(worker).processClaimed(claim);

        scheduler.onJobCommitted(new VerificationWorkAvailable(UUID.randomUUID()));

        assertTrue(completed.await(3, TimeUnit.SECONDS));
    }

    @Test
    void noJobsLeavesSchedulerIdleWithoutRecurringScan() throws Exception {
        when(jobs.dueJobIds(anyInt())).thenReturn(List.of());
        when(jobs.nextEligibleAt()).thenReturn(Optional.empty());
        scheduler.wake();
        // Verify a single scan; no @Scheduled loop starts another one.
        verify(jobs, timeout(2000)).nextEligibleAt();
        Thread.sleep(150);
        verify(jobs, times(1)).nextEligibleAt();
    }

    @Test
    void pendingFutureJobRunsWhenItBecomesDue() throws Exception {
        UUID id = UUID.randomUUID();
        var claim = new VerificationJobManager.ClaimedJob(id, UUID.randomUUID(), "flaky@acme.com", 3);
        CountDownLatch completed = new CountDownLatch(1);
        when(jobs.dueJobIds(anyInt())).thenReturn(List.of(), List.of(id), List.of());
        when(jobs.nextEligibleAt()).thenReturn(Optional.of(Instant.now().plusMillis(120)), Optional.empty());
        when(jobs.claim(id)).thenReturn(claim);
        doAnswer(invocation -> { completed.countDown(); return null; }).when(worker).processClaimed(claim);

        scheduler.wake();

        assertTrue(completed.await(3, TimeUnit.SECONDS));
    }
}
