package com.example.onboarding.onboarding.service;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import com.example.onboarding.onboarding.data.Onboarding;
import com.example.onboarding.onboarding.repository.OnboardingRepository;
import com.example.onboarding.onboarding.data.CreateOnboardingRequest;
import com.example.onboarding.onboarding.data.OnboardingResponse;
import com.example.onboarding.onboarding.data.UpdateContactRequest;
import com.example.onboarding.verification.data.VerificationJob;
import com.example.onboarding.verification.data.VerificationMode;
import com.example.onboarding.verification.repository.VerificationJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class OnboardingService {

    private final OnboardingRepository onboardings;
    private final VerificationJobRepository jobs;

    public OnboardingService(
            OnboardingRepository onboardings,
            VerificationJobRepository jobs
    ) {
        this.onboardings = onboardings;
        this.jobs = jobs;
    }

    @Transactional
    public OnboardingResponse create(
            CreateOnboardingRequest request,
            VerificationMode mode
    ) {
        Onboarding onboarding = new Onboarding(
                request.adminName().trim(),
                normalizeEmail(request.adminEmail()),
                request.companyName().trim(),
                request.companyDomain().trim().toLowerCase(Locale.ROOT)
        );

        onboardings.save(onboarding);

        // ASYNC: job is immediately available to the worker.
        // SYNC: allow the HTTP request to process it first.
        jobs.save(new VerificationJob(
                onboarding.getId(),
                onboarding.getRevision(),
                onboarding.getAdminEmail(),
                firstAttemptAt(mode)
        ));

        return OnboardingResponse.from(onboarding);
    }

    @Transactional(readOnly = true)
    public OnboardingResponse get(UUID id) {
        return OnboardingResponse.from(
                onboardings.findById(id)
                        .orElseThrow(() -> new ResponseStatusException(
                                NOT_FOUND,
                                "Onboarding not found"
                        ))
        );
    }

    @Transactional
    public OnboardingResponse updateContact(
            UUID id,
            UpdateContactRequest request,
            VerificationMode mode
    ) {
        Onboarding onboarding = onboardings.findLockedById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "Onboarding not found"
                ));

        onboarding.updateContact(
                normalizeEmail(request.adminEmail())
        );

        jobs.save(new VerificationJob(
                id,
                onboarding.getRevision(),
                onboarding.getAdminEmail(),
                firstAttemptAt(mode)
        ));

        // Older verification results will be ignored
        // because their revision no longer matches.
        return OnboardingResponse.from(onboarding);
    }

    private static Instant firstAttemptAt(VerificationMode mode) {
        return mode == VerificationMode.SYNC
                ? Instant.now().plusSeconds(10)
                : Instant.now();
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
