package com.example.onboarding.verification.service;

import com.example.onboarding.verification.data.EmailVerifier;
import com.example.onboarding.verification.data.VerificationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class VerificationWorker {
    private static final Logger log = LoggerFactory.getLogger(VerificationWorker.class);
    private final VerificationJobManager jobManager;
    private final EmailVerifier verifier;

    public VerificationWorker(VerificationJobManager jobManager, EmailVerifier verifier) {
        this.jobManager = jobManager;
        this.verifier = verifier;
    }

    @Scheduled(fixedDelayString = "${app.worker.tick-ms:1000}")
    public void processDueJobs() {
        for (UUID jobId : jobManager.dueJobIds()) {
            try {
                VerificationJobManager.ClaimedJob claim = jobManager.claim(jobId);
                if (claim == null) continue;
                VerificationResult result;
                try {
                    result = verifier.verify(claim.email(), claim.attempt());
                } catch (RuntimeException unexpected) {
                    // Never log the API key or email in exception details.
                    log.warn("Unexpected verifier exception: {}", unexpected.getClass().getSimpleName());
                    result = VerificationResult.retryable("unexpected_error");
                }
                jobManager.complete(claim, result);
            } catch (RuntimeException exception) {
                // Leave claimed job leased; it will become eligible after lease expiry.
                log.error("Job processing failed for jobId={}", jobId, exception);
            }
        }
    }
}
