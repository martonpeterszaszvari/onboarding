package com.onboarding.verification.service;
import com.onboarding.verification.data.VerificationResult;
import com.onboarding.verification.data.EmailVerifier;

import com.onboarding.onboarding.data.OnboardingResponse;
import com.onboarding.onboarding.service.OnboardingService;
import com.onboarding.onboarding.data.OnboardingStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class SyncFirstVerificationServiceTest {

    @Test
    void fastSuccessFinishesInlineAndDoesNotQueueAnotherAttempt() {
        OnboardingService onboardings = mock(OnboardingService.class);
        VerificationJobManager jobs = mock(VerificationJobManager.class);
        EmailVerifier verifier = mock(EmailVerifier.class);
        SyncFirstVerificationService service = new SyncFirstVerificationService(onboardings, jobs, verifier, mock(VerificationScheduler.class));
        UUID id = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        OnboardingResponse saved = response(id, OnboardingStatus.PENDING);
        OnboardingResponse completed = response(id, OnboardingStatus.VERIFIED);
        VerificationJobManager.ClaimedJob claim =
            new VerificationJobManager.ClaimedJob(jobId, token, "success@acme.com", 1);

        when(jobs.claimInitial(id, 1)).thenReturn(claim);
        when(verifier.verify("success@acme.com", 1, EmailVerifier.Mode.FAST))
            .thenReturn(VerificationResult.valid());
        when(onboardings.get(id)).thenReturn(completed);

        assertEquals(OnboardingStatus.VERIFIED, service.attempt(saved).status());
        verify(jobs).complete(claim, VerificationResult.valid());
        verify(verifier, times(1)).verify("success@acme.com", 1, EmailVerifier.Mode.FAST);
    }

    @Test
    void unfinishedFastAttemptPassesRetryableResultToDurableJobManager() {
        OnboardingService onboardings = mock(OnboardingService.class);
        VerificationJobManager jobs = mock(VerificationJobManager.class);
        EmailVerifier verifier = mock(EmailVerifier.class);
        SyncFirstVerificationService service = new SyncFirstVerificationService(onboardings, jobs, verifier, mock(VerificationScheduler.class));
        UUID id = UUID.randomUUID();
        VerificationJobManager.ClaimedJob claim = new VerificationJobManager.ClaimedJob(
            UUID.randomUUID(), UUID.randomUUID(), "slow@acme.com", 1);
        when(jobs.claimInitial(id, 1)).thenReturn(claim);
        when(verifier.verify("slow@acme.com", 1, EmailVerifier.Mode.FAST))
            .thenReturn(VerificationResult.retryable("sync_timeout"));
        when(onboardings.get(id)).thenReturn(response(id, OnboardingStatus.VERIFYING));

        assertEquals(OnboardingStatus.VERIFYING, service.attempt(response(id, OnboardingStatus.PENDING)).status());
        verify(jobs).complete(claim, VerificationResult.retryable("sync_timeout"));
    }

    private static OnboardingResponse response(UUID id, OnboardingStatus status) {
        return new OnboardingResponse(id, "Alex", "success@acme.com", "Acme", "acme.com",
            status, null, "test", 1, Instant.now(), Instant.now());
    }
}
