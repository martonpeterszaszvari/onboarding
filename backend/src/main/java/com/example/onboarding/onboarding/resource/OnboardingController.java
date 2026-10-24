package com.example.onboarding.onboarding.resource;

import com.example.onboarding.onboarding.data.OnboardingResponse;
import com.example.onboarding.onboarding.data.OnboardingStatus;
import com.example.onboarding.onboarding.data.CreateOnboardingRequest;
import com.example.onboarding.onboarding.data.UpdateContactRequest;
import com.example.onboarding.onboarding.service.OnboardingService;
import com.example.onboarding.verification.data.VerificationMode;
import com.example.onboarding.verification.service.SyncFirstVerificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/onboardings")
public class OnboardingController {
    private final OnboardingService service;
    private final SyncFirstVerificationService syncVerifier;

    public OnboardingController(OnboardingService service, SyncFirstVerificationService syncVerifier) {
        this.service = service;
        this.syncVerifier = syncVerifier;
    }

    @PostMapping
    public ResponseEntity<OnboardingResponse> create(
            @Valid @RequestBody CreateOnboardingRequest request,
            @RequestParam(defaultValue = "ASYNC") VerificationMode mode) {

        // Both modes persist onboarding + job atomically, then commit.
        OnboardingResponse saved = service.create(request, mode);
        // ASYNC does not call Hunter during this request at all.
        OnboardingResponse result = mode == VerificationMode.SYNC
                ? syncVerifier.attempt(saved) : saved;

        HttpStatus status = isProcessing(result) ? HttpStatus.ACCEPTED : HttpStatus.CREATED;
        return ResponseEntity.status(status)
                .location(URI.create("/api/onboardings/" + result.id()))
                .body(result);
    }

    @GetMapping("/{id}")
    public OnboardingResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PatchMapping("/{id}/contact")
    public ResponseEntity<OnboardingResponse> updateContact(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateContactRequest request,
            @RequestParam(defaultValue = "ASYNC") VerificationMode mode) {

        OnboardingResponse saved = service.updateContact(id, request, mode);
        OnboardingResponse result = mode == VerificationMode.SYNC
                ? syncVerifier.attempt(saved) : saved;
        return ResponseEntity.status(isProcessing(result) ? HttpStatus.ACCEPTED : HttpStatus.OK)
                .body(result);
    }

    private static boolean isProcessing(OnboardingResponse response) {
        return response.status() == OnboardingStatus.PENDING
                || response.status() == OnboardingStatus.VERIFYING;
    }
}
