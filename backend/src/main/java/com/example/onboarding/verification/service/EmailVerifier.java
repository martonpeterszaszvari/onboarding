package com.example.onboarding.verification.service;

import com.example.onboarding.verification.data.VerificationResult;
public interface EmailVerifier {
    VerificationResult verify(String email, int attempt);
}
