package com.example.onboarding.verification.data;

import java.util.UUID;

/** Published inside the transaction; delivered only after the job has committed. */
public record VerificationWorkAvailable(UUID onboardingId) { }
