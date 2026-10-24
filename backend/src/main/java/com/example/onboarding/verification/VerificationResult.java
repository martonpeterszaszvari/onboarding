package com.example.onboarding.verification;

public record VerificationResult(Kind kind, String providerStatus, String message) {
    public enum Kind {
        DELIVERABLE,
        INVALID,
        INCONCLUSIVE,
        PENDING,
        RETRYABLE,
        UNAVAILABLE
    }

    public static VerificationResult valid() {
        return new VerificationResult(Kind.DELIVERABLE, "valid", "Business contact email check passed");
    }
    public static VerificationResult invalid() {
        return new VerificationResult(Kind.INVALID, "invalid", "This email could not be verified. Please correct it.");
    }
    public static VerificationResult inconclusive(String status) {
        return new VerificationResult(Kind.INCONCLUSIVE, status, "Email check inconclusive. Additional review is needed.");
    }
    public static VerificationResult pending() {
        return new VerificationResult(Kind.PENDING, "processing", "Hunter is still processing the email check");
    }
    public static VerificationResult retryable(String category) {
        return new VerificationResult(Kind.RETRYABLE, category, "Verification temporarily delayed; we will retry automatically");
    }
    public static VerificationResult unavailable(String category) {
        return new VerificationResult(Kind.UNAVAILABLE, category, "Verification service unavailable. Contact support or try later.");
    }
}
