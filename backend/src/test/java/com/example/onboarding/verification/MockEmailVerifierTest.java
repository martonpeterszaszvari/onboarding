package com.example.onboarding.verification;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MockEmailVerifierTest {
    private final MockEmailVerifier verifier = new MockEmailVerifier();

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
    void inconclusiveIsNotMarkedVerified() {
        assertEquals(VerificationResult.Kind.INCONCLUSIVE, verifier.verify("risky@acme.com", 1).kind());
        assertEquals(VerificationResult.Kind.INVALID, verifier.verify("invalid@acme.com", 1).kind());
        assertEquals(VerificationResult.Kind.UNAVAILABLE, verifier.verify("quota@acme.com", 1).kind());
    }
}
