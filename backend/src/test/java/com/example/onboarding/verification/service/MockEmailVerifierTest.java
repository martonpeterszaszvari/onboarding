package com.example.onboarding.verification.service;
import com.example.onboarding.verification.data.VerificationResult;
import com.example.onboarding.verification.data.EmailVerifier;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MockEmailVerifierTest {
    private final MockEmailVerifier verifier = new MockEmailVerifier();

    @Test
    void fastTimeoutSucceedsInBackground() {
        assertEquals(VerificationResult.Kind.RETRYABLE,
            verifier.verify("slow@acme.com", 1, EmailVerifier.Mode.FAST).kind());
        assertEquals(VerificationResult.Kind.DELIVERABLE,
            verifier.verify("slow@acme.com", 2, EmailVerifier.Mode.BACKGROUND).kind());
    }

    @Test
    void delayedThenSucceeds() {
        assertEquals(VerificationResult.Kind.PENDING, verifier.verify("delay@acme.com", 1).kind());
        assertEquals(VerificationResult.Kind.PENDING, verifier.verify("delay@acme.com", 2).kind());
        assertEquals(VerificationResult.Kind.DELIVERABLE, verifier.verify("delay@acme.com", 3).kind());
    }

    @Test
    void transientFailureThenSucceeds() {
        assertEquals(VerificationResult.Kind.RETRYABLE, verifier.verify("flaky@acme.com", 1).kind());
        assertEquals(VerificationResult.Kind.DELIVERABLE, verifier.verify("flaky@acme.com", 3).kind());
    }

    @Test
    void retryExhaustionScenarioNeverSucceeds() {
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertEquals(VerificationResult.Kind.RETRYABLE,
                verifier.verify("exhaust@acme.com", attempt).kind());
        }
    }

    @Test
    void inconclusiveIsNotMarkedVerified() {
        assertEquals(VerificationResult.Kind.INCONCLUSIVE, verifier.verify("risky@acme.com", 1).kind());
        assertEquals(VerificationResult.Kind.INVALID, verifier.verify("invalid@acme.com", 1).kind());
        assertEquals(VerificationResult.Kind.UNAVAILABLE, verifier.verify("quota@acme.com", 1).kind());
    }
}
