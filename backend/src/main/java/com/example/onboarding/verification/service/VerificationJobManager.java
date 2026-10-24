package com.example.onboarding.verification.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.example.onboarding.onboarding.data.Onboarding;
import com.example.onboarding.onboarding.data.OnboardingStatus;
import com.example.onboarding.onboarding.repository.OnboardingRepository;
import com.example.onboarding.verification.data.JobStatus;
import com.example.onboarding.verification.data.VerificationJob;
import com.example.onboarding.verification.data.VerificationResult;
import com.example.onboarding.verification.repository.VerificationJobRepository;
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

    public VerificationJobManager(
            VerificationJobRepository jobs,
            OnboardingRepository onboardings,
            @Value("${app.worker.lease-seconds:60}") int leaseSeconds,
            @Value("${app.worker.pending-delay-seconds:4}") int pendingDelaySeconds,
            @Value("${app.worker.retry-base-seconds:2}") int retryBaseSeconds,
            @Value("${app.worker.max-attempts:5}") int maxAttempts
    ) {
        this.jobs = jobs;
        this.onboardings = onboardings;
        this.leaseSeconds = leaseSeconds;
        this.pendingDelaySeconds = pendingDelaySeconds;
        this.retryBaseSeconds = retryBaseSeconds;
        this.maxAttempts = maxAttempts;
    }

    public record ClaimedJob(
            UUID jobId,
            UUID token,
            String email,
            int attempt
    ) {
    }

    public List<UUID> dueJobIds() {
        return jobs.findDue(
                JobStatus.PENDING,
                JobStatus.PROCESSING,
                Instant.now(),
                PageRequest.of(0, 10)
        );
    }

    // Called from the original HTTP request.
    @Transactional
    public ClaimedJob claimInitial(UUID onboardingId, int revision) {
        VerificationJob job = jobs
                .findByOnboardingIdAndRevision(onboardingId, revision)
                .orElseThrow();

        UUID token = UUID.randomUUID();

        int claimed = jobs.claimInitial(
                job.getId(),
                JobStatus.PENDING,
                JobStatus.PROCESSING,
                token,
                Instant.now().plusSeconds(leaseSeconds)
        );

        if (claimed == 0) return null;

        return prepareClaim(job.getId(), token);
    }

    // Called by the scheduled worker.
    @Transactional
    public ClaimedJob claim(UUID jobId) {
        Instant now = Instant.now();
        UUID token = UUID.randomUUID();

        int claimed = jobs.claim(
                jobId,
                JobStatus.PENDING,
                JobStatus.PROCESSING,
                token,
                now,
                now.plusSeconds(leaseSeconds)
        );

        if (claimed == 0) return null;

        return prepareClaim(jobId, token);
    }

    private ClaimedJob prepareClaim(UUID jobId, UUID token) {
        VerificationJob job = jobs.findById(jobId).orElseThrow();

        Onboarding onboarding = onboardings
                .findLockedById(job.getOnboardingId())
                .orElseThrow();

        // An older email verification cannot update newer data.
        if (job.getRevision() != onboarding.getRevision()) {
            job.cancel();
            return null;
        }

        onboarding.setVerification(
                OnboardingStatus.VERIFYING,
                null,
                "Contact verification is running"
        );

        return new ClaimedJob(
                jobId,
                token,
                job.getEmail(),
                job.getAttemptCount()
        );
    }

    @Transactional
    public void complete(ClaimedJob claim, VerificationResult result) {
        VerificationJob job = jobs.findById(claim.jobId()).orElseThrow();

        // Ignore late responses from workers that lost their lease.
        if (job.getStatus() != JobStatus.PROCESSING
                || !claim.token().equals(job.getClaimToken())) {
            return;
        }

        Onboarding onboarding = onboardings
                .findLockedById(job.getOnboardingId())
                .orElseThrow();

        if (job.getRevision() != onboarding.getRevision()) {
            job.cancel();
            return;
        }

        switch (result.kind()) {
            case DELIVERABLE -> {
                job.finish();
                onboarding.setVerification(
                        OnboardingStatus.VERIFIED,
                        result.providerStatus(),
                        result.message()
                );
            }

            case INVALID -> {
                job.finish();
                onboarding.setVerification(
                        OnboardingStatus.ACTION_REQUIRED,
                        result.providerStatus(),
                        result.message()
                );
            }

            case INCONCLUSIVE -> {
                job.finish();
                onboarding.setVerification(
                        OnboardingStatus.REVIEW_REQUIRED,
                        result.providerStatus(),
                        result.message()
                );
            }

            case UNAVAILABLE -> {
                job.finish();
                onboarding.setVerification(
                        OnboardingStatus.TEMPORARILY_UNAVAILABLE,
                        result.providerStatus(),
                        result.message()
                );
            }

            case PENDING, RETRYABLE -> {
                if (job.getAttemptCount() >= maxAttempts) {
                    job.finish();
                    onboarding.setVerification(
                            OnboardingStatus.REVIEW_REQUIRED,
                            result.providerStatus(),
                            "Automatic retries exhausted. Review required."
                    );
                    return;
                }

                long delay;

                if (result.kind() == VerificationResult.Kind.PENDING) {
                    delay = pendingDelaySeconds;
                } else {
                    int exponent = Math.min(
                            job.getAttemptCount() - 1, 5
                    );
                    delay = Math.min(
                            60L,
                            (long) retryBaseSeconds * (1L << exponent)
                    );
                }

                job.reschedule(Instant.now().plusSeconds(delay));

                onboarding.setVerification(
                        OnboardingStatus.VERIFYING,
                        result.providerStatus(),
                        result.message()
                );
            }
        }
    }
}
