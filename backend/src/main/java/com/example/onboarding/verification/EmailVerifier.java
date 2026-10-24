package com.example.onboarding.verification;

public interface EmailVerifier {
    VerificationResult verify(String email, int attempt);
}
