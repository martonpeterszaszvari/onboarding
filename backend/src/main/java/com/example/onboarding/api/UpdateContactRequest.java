package com.example.onboarding.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateContactRequest(@NotBlank @Email @Size(max = 255) String adminEmail) { }
