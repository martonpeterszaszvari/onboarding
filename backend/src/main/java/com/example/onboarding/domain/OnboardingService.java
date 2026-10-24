package com.example.onboarding.domain;

import com.example.onboarding.api.CreateOnboardingRequest;
import com.example.onboarding.api.OnboardingResponse;
import com.example.onboarding.api.UpdateContactRequest;
import java.util.Locale;
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
    public OnboardingResponse create(CreateOnboardingRequest request) {
        Onboarding onboarding = new Onboarding(
            request.adminName().trim(), normalizeEmail(request.adminEmail()),
            request.companyName().trim(), request.companyDomain().trim().toLowerCase(Locale.ROOT));
        onboardings.save(onboarding);
        // Both records commit together: no 'accepted' response without a durable job.
        jobs.save(new VerificationJob(onboarding.getId(), onboarding.getRevision(), onboarding.getAdminEmail()));
        return OnboardingResponse.from(onboarding);
    }

    @Transactional(readOnly = true)
    public OnboardingResponse get(UUID id) {
        return OnboardingResponse.from(onboardings.findById(id)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Onboarding not found")));
    }

    @Transactional
    public OnboardingResponse updateContact(UUID id, UpdateContactRequest request) {
        Onboarding onboarding = onboardings.findLockedById(id)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Onboarding not found"));
        onboarding.updateContact(normalizeEmail(request.adminEmail()));
        jobs.save(new VerificationJob(id, onboarding.getRevision(), onboarding.getAdminEmail()));
        // Old jobs may finish after this point; their revision will be ignored.
        return OnboardingResponse.from(onboarding);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
