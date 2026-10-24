package com.example.onboarding.onboarding.resource;

import com.example.onboarding.onboarding.data.OnboardingStatus;
import com.example.onboarding.onboarding.service.OnboardingService;
import com.example.onboarding.verification.service.SyncFirstVerificationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/onboardings")
public class OnboardingController {

    private final OnboardingService onboardingService;
    private final SyncFirstVerificationService verificationService;

    public OnboardingController(
            OnboardingService onboardingService,
            SyncFirstVerificationService verificationService
    ) {
        this.onboardingService = onboardingService;
        this.verificationService = verificationService;
    }

    @PostMapping
    public ResponseEntity<OnboardingResponse> create(
            @Valid @RequestBody CreateOnboardingRequest request
    ) {
        // Save onboarding and job in one database transaction.
        OnboardingResponse created = onboardingService.create(request);

        // Try Hunter synchronously after the transaction commits.
        OnboardingResponse result =
                verificationService.tryImmediately(created);

        HttpStatus status = isProcessing(result)
                ? HttpStatus.ACCEPTED
                : HttpStatus.CREATED;

        return ResponseEntity.status(status)
                .location(URI.create("/api/onboardings/" + result.id()))
                .body(result);
    }

    @GetMapping("/{id}")
    public OnboardingResponse get(@PathVariable UUID id) {
        return onboardingService.get(id);
    }

    @PatchMapping("/{id}/contact")
    public ResponseEntity<OnboardingResponse> updateContact(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateContactRequest request
    ) {
        OnboardingResponse updated =
                onboardingService.updateContact(id, request);

        OnboardingResponse result =
                verificationService.tryImmediately(updated);

        HttpStatus status = isProcessing(result)
                ? HttpStatus.ACCEPTED
                : HttpStatus.OK;

        return ResponseEntity.status(status).body(result);
    }

    private boolean isProcessing(OnboardingResponse response) {
        return response.status() == OnboardingStatus.PENDING
                || response.status() == OnboardingStatus.VERIFYING;
    }
}

