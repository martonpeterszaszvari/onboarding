package com.example.onboarding.verification.service;

import com.example.onboarding.verification.data.VerificationResult;

public interface EmailVerifier {

    enum Mode {
        FAST,
        BACKGROUND
    }

    VerificationResult verify(
            String email,
            int attempt,
            Mode mode
    );

    // Your existing scheduled worker can keep calling
    // verify(email, attempt), without any changes.
    default VerificationResult verify(String email, int attempt) {
        return verify(email, attempt, Mode.BACKGROUND);
    }
}
