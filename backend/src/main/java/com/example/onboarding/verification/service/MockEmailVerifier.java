package com.example.onboarding.verification.service;

import com.example.onboarding.verification.data.VerificationResult;
import com.example.onboarding.verification.service.EmailVerifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "hunter.mode",
        havingValue = "mock",
        matchIfMissing = true
)
public class MockEmailVerifier implements EmailVerifier {

    @Override
    public VerificationResult verify(
            String email,
            int attempt,
            Mode mode
    ) {
        String username = email
                .substring(0, email.indexOf('@'))
                .toLowerCase();

        if (username.startsWith("invalid")) {
            return VerificationResult.invalid();
        }

        if (username.startsWith("risky")) {
            return VerificationResult.inconclusive("accept_all");
        }

        if (username.startsWith("quota")) {
            return VerificationResult.unavailable("quota_exceeded");
        }
        if (username.startsWith("exhaust")) {
            return VerificationResult.retryable("simulated_timeout");
        }
        // Simulates a synchronous request exceeding
        // its timeout, but succeeding in the background.
        if (username.startsWith("slow")) {
            if (mode == Mode.FAST) {
                return VerificationResult.retryable("sync_timeout");
            }
            return VerificationResult.valid();
        }

        // Simulates Hunter responding with HTTP 202.
        if (username.startsWith("delay") && attempt <= 2) {
            return VerificationResult.pending();
        }

        if (username.startsWith("flaky") && attempt <= 2) {
            return VerificationResult.retryable("temporary_503");
        }

        return VerificationResult.valid();
    }
}
