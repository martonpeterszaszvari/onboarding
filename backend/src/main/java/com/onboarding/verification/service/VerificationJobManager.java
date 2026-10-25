package com.onboarding.verification.service;

import com.onboarding.onboarding.entity.Onboarding;
import com.onboarding.onboarding.data.OnboardingStatus;
import com.onboarding.onboarding.repository.OnboardingRepository;
import com.onboarding.verification.data.JobStatus;
import com.onboarding.verification.entity.VerificationJob;
import com.onboarding.verification.data.VerificationResult;
import com.onboarding.verification.repository.VerificationJobRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VerificationJobManager {
    private final VerificationJobRepository jobs;
    private final OnboardingRepository onboardings;
    private final int leaseSeconds;
    private final int pendingDelaySeconds;
    private final int retryBaseSeconds;
    private final int maxAttempts;

    public VerificationJobManager(VerificationJobRepository jobs, OnboardingRepository onboardings,
        @Value("${app.worker.lease-seconds:60}") int leaseSeconds,
        @Value("${app.worker.pending-delay-seconds:4}") int pendingDelaySeconds,
        @Value("${app.worker.retry-base-seconds:2}") int retryBaseSeconds,
        @Value("${app.worker.max-attempts:5}") int maxAttempts) {
        this.jobs = jobs;
        this.onboardings = onboardings;
        this.leaseSeconds = leaseSeconds;
        this.pendingDelaySeconds = pendingDelaySeconds;
        this.retryBaseSeconds = retryBaseSeconds;
        this.maxAttempts = maxAttempts;
    }

    public List<UUID> dueJobIds(int limit) {
        return jobs.findDue(JobStatus.PENDING, JobStatus.PROCESSING,
            Instant.now(), PageRequest.of(0, Math.max(1, limit)));
    }

    /** Next time a pending job becomes due, or a processing lease expires. */
    @Transactional(readOnly = true)
    public java.util.Optional<Instant> nextEligibleAt() {
        var pending = jobs.earliestPending(JobStatus.PENDING);
        var processing = jobs.earliestProcessingLease(JobStatus.PROCESSING);
        if (pending.isEmpty()) return processing;
        if (processing.isEmpty()) return pending;
        return java.util.Optional.of(pending.get().isBefore(processing.get())
            ? pending.get() : processing.get());
    }

    public record ClaimedJob(UUID jobId, UUID token, String email, int attempt) { }

    // The request thread reserves its brand-new job before the scheduled worker.
    @Transactional
    public ClaimedJob claimInitial(UUID onboardingId, int revision) {
        VerificationJob job = jobs.findByOnboardingIdAndRevision(onboardingId, revision)
            .orElseThrow();
        UUID token = UUID.randomUUID();
        int claimed = jobs.claimInitial(job.getId(), JobStatus.PENDING,
            JobStatus.PROCESSING, token, Instant.now().plusSeconds(leaseSeconds));
        return claimed == 0 ? null : prepareClaim(job.getId(), token);
    }

    // A conditional DB update ensures competing workers cannot claim the same lease.
    @Transactional
    public ClaimedJob claim(UUID id) {
        Instant now = Instant.now();
        UUID token = UUID.randomUUID();
        int claimed = jobs.claim(id, JobStatus.PENDING, JobStatus.PROCESSING, token,
            now, now.plusSeconds(leaseSeconds));
        return claimed == 0 ? null : prepareClaim(id, token);
    }

    private ClaimedJob prepareClaim(UUID id, UUID token) {
        VerificationJob job = jobs.findById(id).orElseThrow();
        Onboarding onboarding = onboardings.findLockedById(job.getOnboardingId()).orElseThrow();
        if (job.getRevision() != onboarding.getRevision()) {
            job.cancel();
            return null;
        }
        onboarding.setVerification(OnboardingStatus.VERIFYING, null, "Contact verification is running");
        return new ClaimedJob(id, token, job.getEmail(), job.getAttemptCount());
    }

    @Transactional
    public void complete(ClaimedJob claim, VerificationResult result) {
        VerificationJob job = jobs.findLockedById(claim.jobId()).orElseThrow();
        if (job.getStatus() != JobStatus.PROCESSING || !claim.token().equals(job.getClaimToken())) {
            return; // An expired lease may have been claimed by another worker.
        }
        Onboarding onboarding = onboardings.findLockedById(job.getOnboardingId()).orElseThrow();
        if (job.getRevision() != onboarding.getRevision()) {
            job.cancel(); // Ignore a late result for a previous email address.
            return;
        }
        switch (result.kind()) {
            case DELIVERABLE -> {
                job.finish();
                onboarding.setVerification(OnboardingStatus.VERIFIED, result.providerStatus(), result.message());
            }
            case INVALID -> {
                job.finish();
                onboarding.setVerification(OnboardingStatus.ACTION_REQUIRED, result.providerStatus(), result.message());
            }
            case INCONCLUSIVE -> {
                job.finish();
                onboarding.setVerification(OnboardingStatus.REVIEW_REQUIRED, result.providerStatus(), result.message());
            }
            case UNAVAILABLE -> {
                job.finish();
                onboarding.setVerification(OnboardingStatus.TEMPORARILY_UNAVAILABLE, result.providerStatus(), result.message());
            }
            case PENDING, RETRYABLE -> {
                if (job.getAttemptCount() >= maxAttempts) {
                    job.finish();
                    onboarding.setVerification(OnboardingStatus.REVIEW_REQUIRED,
                        result.providerStatus(), "Automatic retries exhausted. Additional review is required.");
                } else {
                    boolean stillProcessing = result.kind() == VerificationResult.Kind.PENDING
                        || "network_timeout".equals(result.providerStatus())
                        || "sync_timeout".equals(result.providerStatus());
                    long delay = stillProcessing
                        ? pendingDelaySeconds
                        : Math.min(60, (long) retryBaseSeconds * (1L << Math.min(job.getAttemptCount() - 1, 5)));
                    job.reschedule(Instant.now().plusSeconds(delay));
                    onboarding.setVerification(OnboardingStatus.VERIFYING,
                        result.providerStatus(), result.message());
                }
            }
        }
    }
}
