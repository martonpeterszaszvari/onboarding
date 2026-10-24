package com.example.onboarding.onboarding.data;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateOnboardingRequest(
    @NotBlank @Size(max = 120) String adminName,
    @NotBlank @Email @Size(max = 255) String adminEmail,
    @NotBlank @Size(max = 160) String companyName,
    @NotBlank @Pattern(regexp = "(?i)^[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?\\.[a-z]{2,}$",
      message = "must be a domain such as example.com, without https://") @Size(max = 255) String companyDomain
) { }
