package com.example.onboarding.verification.data;

public interface EmailVerifier {
    enum Mode { FAST, BACKGROUND }

    VerificationResult verify(String email, int attempt, Mode mode);

    default VerificationResult verify(String email, int attempt) {
        return verify(email, attempt, Mode.BACKGROUND);
    }
}
