package com.example.onboarding.verification.service;

import com.example.onboarding.onboarding.data.OnboardingResponse;
import com.example.onboarding.onboarding.service.OnboardingService;
import com.example.onboarding.verification.data.EmailVerifier;
import com.example.onboarding.verification.data.VerificationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Runs only the first verification attempt inline; unfinished work remains durable. */
@Service
public class SyncFirstVerificationService {
    private static final Logger log = LoggerFactory.getLogger(SyncFirstVerificationService.class);

    private final OnboardingService onboardings;
    private final VerificationJobManager jobs;
    private final EmailVerifier verifier;

    public SyncFirstVerificationService(OnboardingService onboardings,
                                        VerificationJobManager jobs, EmailVerifier verifier) {
        this.onboardings = onboardings;
        this.jobs = jobs;
        this.verifier = verifier;
    }

    // The caller MUST have completed the creating transaction before invoking this.
    // No database transaction is open while waiting for Hunter.
    public OnboardingResponse attempt(OnboardingResponse registered) {
        VerificationJobManager.ClaimedJob claim =
                jobs.claimInitial(registered.id(), registered.revision());
        if (claim == null) return onboardings.get(registered.id());

        VerificationResult result;
        try {
            result = verifier.verify(claim.email(), claim.attempt(), EmailVerifier.Mode.FAST);
        } catch (RuntimeException exception) {
            log.warn("Fast verification threw {}", exception.getClass().getSimpleName());
            result = VerificationResult.retryable("unexpected_error");
        }
        // Terminal result => DONE. PENDING/RETRYABLE => rescheduled for the worker.
        jobs.complete(claim, result);
        return onboardings.get(registered.id());
    }
}
