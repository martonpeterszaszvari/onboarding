package com.example.onboarding.onboarding.resource;

import com.example.onboarding.onboarding.data.Onboarding;
import com.example.onboarding.onboarding.data.OnboardingStatus;
import java.time.Instant;
import java.util.UUID;

public record OnboardingResponse(
    UUID id,
    String adminName,
    String adminEmail,
    String companyName,
    String companyDomain,
    OnboardingStatus status,
    String providerStatus,
    String statusMessage,
    int revision,
    Instant createdAt,
    Instant updatedAt
) {
    public static OnboardingResponse from(Onboarding onboarding) {
        return new OnboardingResponse(onboarding.getId(), onboarding.getAdminName(),
            onboarding.getAdminEmail(), onboarding.getCompanyName(), onboarding.getCompanyDomain(),
            onboarding.getStatus(), onboarding.getProviderStatus(), onboarding.getStatusMessage(),
            onboarding.getRevision(), onboarding.getCreatedAt(), onboarding.getUpdatedAt());
    }
}
