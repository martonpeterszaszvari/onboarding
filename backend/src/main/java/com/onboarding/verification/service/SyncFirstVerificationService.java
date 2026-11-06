package com.onboarding.verification.service;

import com.onboarding.verification.data.EmailVerifier;
import com.onboarding.verification.data.VerificationResult;

import com.onboarding.onboarding.data.OnboardingResponse;
import com.onboarding.onboarding.service.OnboardingService;
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
    private final VerificationScheduler scheduler;

    public SyncFirstVerificationService(OnboardingService onboardings,
        VerificationJobManager jobs, EmailVerifier verifier, VerificationScheduler scheduler) {
        this.onboardings = onboardings;
        this.jobs = jobs;
        this.verifier = verifier;
        this.scheduler = scheduler;
    }

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
        try {
            jobs.complete(claim, result);
        } finally {
            scheduler.wake();
        }
        return onboardings.get(registered.id());
    }
}
