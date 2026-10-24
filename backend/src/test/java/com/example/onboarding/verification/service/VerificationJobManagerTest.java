package com.example.onboarding.verification.service;

import com.example.onboarding.onboarding.repository.OnboardingRepository;
import com.example.onboarding.verification.data.JobStatus;
import com.example.onboarding.verification.repository.VerificationJobRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class VerificationJobManagerTest {
    private final VerificationJobRepository jobs = mock(VerificationJobRepository.class);
    private final OnboardingRepository onboardings = mock(OnboardingRepository.class);
    private final VerificationJobManager manager = new VerificationJobManager(
        jobs, onboardings, 60, 4, 2, 5);

    @Test
    void earliestWakeConsidersBothPendingRetriesAndExpiredProcessingLeases() {
        Instant pending = Instant.parse("2026-10-24T14:00:00Z");
        Instant lease = Instant.parse("2026-10-24T13:59:00Z");
        when(jobs.earliestPending(JobStatus.PENDING)).thenReturn(Optional.of(pending));
        when(jobs.earliestProcessingLease(JobStatus.PROCESSING)).thenReturn(Optional.of(lease));

        assertEquals(Optional.of(lease), manager.nextEligibleAt());
    }

    @Test
    void absenceOfUnfinishedJobsMeansNoNextScheduledWake() {
        when(jobs.earliestPending(JobStatus.PENDING)).thenReturn(Optional.empty());
        when(jobs.earliestProcessingLease(JobStatus.PROCESSING)).thenReturn(Optional.empty());

        assertTrue(manager.nextEligibleAt().isEmpty());
    }
}
