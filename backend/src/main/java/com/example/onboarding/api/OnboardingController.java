package com.example.onboarding.api;

import com.example.onboarding.domain.OnboardingService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/onboardings")
public class OnboardingController {
    private final OnboardingService service;

    public OnboardingController(OnboardingService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<OnboardingResponse> create(@Valid @RequestBody CreateOnboardingRequest request) {
        OnboardingResponse response = service.create(request);
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/{id}")
    public OnboardingResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PatchMapping("/{id}/contact")
    public ResponseEntity<OnboardingResponse> updateContact(@PathVariable UUID id,
        @Valid @RequestBody UpdateContactRequest request) {
        return ResponseEntity.accepted().body(service.updateContact(id, request));
    }
}
