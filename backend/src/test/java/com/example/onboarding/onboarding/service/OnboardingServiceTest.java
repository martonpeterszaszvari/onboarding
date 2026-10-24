package com.example.onboarding.onboarding.service;

import com.example.onboarding.onboarding.data.Onboarding;
import com.example.onboarding.onboarding.data.OnboardingStatus;
import com.example.onboarding.onboarding.repository.OnboardingRepository;
import com.example.onboarding.verification.repository.VerificationJobRepository;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import com.example.onboarding.verification.data.VerificationWorkAvailable;
import com.example.onboarding.verification.data.VerificationMode;
import com.example.onboarding.onboarding.data.CreateOnboardingRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OnboardingServiceTest {
    @Test
    void creatingAnAsyncOnboardingPublishesAnAfterCommitWakeUpEvent() {
        OnboardingRepository repository = mock(OnboardingRepository.class);
        VerificationJobRepository jobs = mock(VerificationJobRepository.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        OnboardingService service = new OnboardingService(repository, jobs, events);

        var saved = service.create(new CreateOnboardingRequest(
            "Alex", "TEST@acme.com", "Acme", "acme.com"), VerificationMode.ASYNC);

        assertEquals("test@acme.com", saved.adminEmail());
        verify(repository).save(any(Onboarding.class));
        verify(jobs).save(any(com.example.onboarding.verification.data.VerificationJob.class));
        verify(events).publishEvent(new VerificationWorkAvailable(saved.id()));
    }

    @Test
    void recentOnboardingsAreMappedAndLimitIsCappedAtOneHundred() {
        OnboardingRepository repository = mock(OnboardingRepository.class);
        VerificationJobRepository jobs = mock(VerificationJobRepository.class);
        OnboardingService service = new OnboardingService(repository, jobs, mock(ApplicationEventPublisher.class));
        Onboarding record = new Onboarding("Alex", "alex@acme.com", "Acme", "acme.com");
        record.setVerification(OnboardingStatus.VERIFIED, "valid", "Email check passed");
        when(repository.findAllByOrderByCreatedAtDesc(any(Pageable.class)))
            .thenReturn(List.of(record));

        var result = service.listRecent(9999);

        assertEquals(1, result.size());
        assertEquals(OnboardingStatus.VERIFIED, result.getFirst().status());
        ArgumentCaptor<Pageable> pages = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAllByOrderByCreatedAtDesc(pages.capture());
        assertEquals(100, pages.getValue().getPageSize());
    }
}
