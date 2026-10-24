package com.example.onboarding.verification.service;

import com.example.onboarding.verification.data.EmailVerifier;
import com.example.onboarding.verification.data.VerificationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Executes exactly one ALREADY CLAIMED job, on the dedicated worker pool. */
@Component
public class VerificationWorker {
    private static final Logger log = LoggerFactory.getLogger(VerificationWorker.class);
    private final VerificationJobManager jobManager;
    private final EmailVerifier verifier;

    public VerificationWorker(VerificationJobManager jobManager, EmailVerifier verifier) {
        this.jobManager = jobManager;
        this.verifier = verifier;
    }

    public void processClaimed(VerificationJobManager.ClaimedJob claim) {
        log.info("Background verification starting job={} attempt={}", claim.jobId(), claim.attempt());
        VerificationResult result;
        try {
            result = verifier.verify(claim.email(), claim.attempt(), EmailVerifier.Mode.BACKGROUND);
        } catch (RuntimeException exception) {
            // Avoid logging email addresses, credentials, or provider URLs.
            log.warn("Provider call raised {}", exception.getClass().getSimpleName());
            result = VerificationResult.retryable("unexpected_error");
        }
        // Must run outside a provider HTTP transaction; the manager opens a short DB transaction.
        jobManager.complete(claim, result);
        log.info("Background verification finished job={} outcome={}", claim.jobId(), result.kind());
    }
}
