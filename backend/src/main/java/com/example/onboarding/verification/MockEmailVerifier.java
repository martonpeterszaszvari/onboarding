package com.example.onboarding.verification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Reproducible scenarios: no external API or API key required. */
@Component
@ConditionalOnProperty(name = "hunter.mode", havingValue = "mock", matchIfMissing = true)
public class MockEmailVerifier implements EmailVerifier {
    @Override
    public VerificationResult verify(String email, int attempt) {
        String username = email.substring(0, email.indexOf('@')).toLowerCase();
        if (username.startsWith("invalid")) return VerificationResult.invalid();
        if (username.startsWith("risky")) return VerificationResult.inconclusive("accept_all");
        if (username.startsWith("quota")) return VerificationResult.unavailable("quota_exceeded");
        if (username.startsWith("delay") && attempt <= 2) return VerificationResult.pending();
        if (username.startsWith("flaky") && attempt <= 2) return VerificationResult.retryable("temporary_503");
        return VerificationResult.valid();
    }
}
