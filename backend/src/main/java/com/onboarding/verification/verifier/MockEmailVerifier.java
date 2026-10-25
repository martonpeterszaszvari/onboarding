package com.onboarding.verification.verifier;

import com.onboarding.verification.data.EmailVerifier;
import com.onboarding.verification.data.VerificationResult;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Reproducible scenarios: no external API or API key required. */
@Component
@ConditionalOnProperty(name = "hunter.mode", havingValue = "mock", matchIfMissing = true)
public class MockEmailVerifier implements EmailVerifier {
    @Override
    public VerificationResult verify(String email, int attempt, Mode mode) {
        String username = email.substring(0, email.indexOf('@')).toLowerCase();
        // A deterministic example of the FAST attempt reaching its time budget.
        // The worker's BACKGROUND attempt will succeed.
        if (username.startsWith("slow") && mode == Mode.FAST)
            return VerificationResult.retryable("sync_timeout");
        if (username.startsWith("invalid")) return VerificationResult.invalid();
        if (username.startsWith("risky")) return VerificationResult.inconclusive("accept_all");
        if (username.startsWith("quota")) return VerificationResult.unavailable("quota_exceeded");
        if (username.startsWith("delay") && attempt <= 2) return VerificationResult.pending();
        if (username.startsWith("flaky") && attempt <= 2) return VerificationResult.retryable("temporary_503");
        if (username.startsWith("exhaust")) return VerificationResult.retryable("simulated_timeout");
        return VerificationResult.valid();
    }
}
