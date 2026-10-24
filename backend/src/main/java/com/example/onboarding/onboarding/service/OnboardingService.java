package com.example.onboarding.onboarding.service;

import com.example.onboarding.onboarding.data.Onboarding;
import com.example.onboarding.onboarding.repository.OnboardingRepository;
import com.example.onboarding.verification.repository.VerificationJobRepository;
import com.example.onboarding.verification.data.VerificationMode;
import com.example.onboarding.verification.data.VerificationJob;

import com.example.onboarding.onboarding.data.CreateOnboardingRequest;
import com.example.onboarding.onboarding.data.OnboardingResponse;
import com.example.onboarding.onboarding.data.UpdateContactRequest;
import java.time.Instant;
import java.util.Locale;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class OnboardingService {
    private final OnboardingRepository onboardings;
    private final VerificationJobRepository jobs;

    public OnboardingService(OnboardingRepository onboardings, VerificationJobRepository jobs) {
        this.onboardings = onboardings;
        this.jobs = jobs;
    }

    @Transactional
    public OnboardingResponse create(CreateOnboardingRequest request, VerificationMode mode) {
        Onboarding onboarding = new Onboarding(
                request.adminName().trim(), normalizeEmail(request.adminEmail()),
                request.companyName().trim(), request.companyDomain().trim().toLowerCase(Locale.ROOT));
        onboardings.save(onboarding);
        // ASYNC jobs are due immediately. A SYNC job gets a short grace window,
        // allowing the HTTP request to claim it first. If the app crashes after
        // committing but before the first claim, the worker will pick it up.
        jobs.save(new VerificationJob(onboarding.getId(), onboarding.getRevision(),
                onboarding.getAdminEmail(), firstAttemptAt(mode)));
        return OnboardingResponse.from(onboarding);
    }

    // Recent business registrations and their latest verification outcomes.
    // Capped to keep a demo endpoint from loading the entire database.
    @Transactional(readOnly = true)
    public List<OnboardingResponse> listRecent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return onboardings.findAllByOrderByCreatedAtDesc(PageRequest.of(0, safeLimit))
                .stream().map(OnboardingResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public OnboardingResponse get(UUID id) {
        return OnboardingResponse.from(onboardings.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Onboarding not found")));
    }

    @Transactional
    public OnboardingResponse updateContact(UUID id, UpdateContactRequest request, VerificationMode mode) {
        Onboarding onboarding = onboardings.findLockedById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Onboarding not found"));
        onboarding.updateContact(normalizeEmail(request.adminEmail()));
        jobs.save(new VerificationJob(id, onboarding.getRevision(),
                onboarding.getAdminEmail(), firstAttemptAt(mode)));
        // Old jobs may finish after this point; their revision will be ignored.
        return OnboardingResponse.from(onboarding);
    }

    private static Instant firstAttemptAt(VerificationMode mode) {
        // Never rely on this grace period as the only durability mechanism.
        return mode == VerificationMode.SYNC
                ? Instant.now().plusSeconds(10)
                : Instant.now();
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
