package com.example.onboarding.verification.service;

import com.example.onboarding.onboarding.resource.OnboardingResponse;
import com.example.onboarding.onboarding.service.OnboardingService;
import com.example.onboarding.verification.data.VerificationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SyncFirstVerificationService {

    private static final Logger log =
            LoggerFactory.getLogger(SyncFirstVerificationService.class);

    private final VerificationJobManager jobManager;
    private final EmailVerifier verifier;
    private final OnboardingService onboardingService;

    public SyncFirstVerificationService(
            VerificationJobManager jobManager,
            EmailVerifier verifier,
            OnboardingService onboardingService
    ) {
        this.jobManager = jobManager;
        this.verifier = verifier;
        this.onboardingService = onboardingService;
    }

    public OnboardingResponse tryImmediately(
            OnboardingResponse onboarding
    ) {
        // The registration transaction has already committed.
        // Claim the persisted job before calling Hunter.
        VerificationJobManager.ClaimedJob claim =
                jobManager.claimInitial(
                        onboarding.id(),
                        onboarding.revision()
                );

        // Another processor may already own the job.
        if (claim == null) {
            return onboardingService.get(onboarding.id());
        }

        VerificationResult result;

        try {
            result = verifier.verify(
                    claim.email(),
                    claim.attempt(),
                    EmailVerifier.Mode.FAST
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "Fast verification failed: {}",
                    exception.getClass().getSimpleName()
            );

            result = VerificationResult.retryable(
                    "unexpected_error"
            );
        }

        // A completed response finishes the job.
        // A pending/timeout response reschedules it.
        jobManager.complete(claim, result);

        return onboardingService.get(onboarding.id());
    }
}
