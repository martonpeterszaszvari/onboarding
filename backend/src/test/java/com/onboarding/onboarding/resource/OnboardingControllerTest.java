package com.onboarding.onboarding.resource;
import com.onboarding.onboarding.data.OnboardingResponse;
import com.onboarding.onboarding.data.CreateOnboardingRequest;

import com.onboarding.onboarding.service.OnboardingService;
import com.onboarding.onboarding.data.OnboardingStatus;
import com.onboarding.verification.data.VerificationMode;
import com.onboarding.verification.service.SyncFirstVerificationService;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class OnboardingControllerTest {
    private final OnboardingService service = mock(OnboardingService.class);
    private final SyncFirstVerificationService sync = mock(SyncFirstVerificationService.class);
    private final OnboardingController controller = new OnboardingController(service, sync);
    private final CreateOnboardingRequest request =
        new CreateOnboardingRequest("Alex", "success@acme.com", "Acme", "acme.com");

    @Test
    void listReturnsPriorRegistrationsWithoutInvokingVerifier() {
        OnboardingResponse first = response(OnboardingStatus.VERIFIED);
        OnboardingResponse second = response(OnboardingStatus.REVIEW_REQUIRED);
        when(service.listRecent(50)).thenReturn(List.of(first, second));

        assertEquals(List.of(first, second), controller.list(50));
        verifyNoInteractions(sync);
    }

    @Test
    void asyncReturnsAcceptedWithoutCallingProviderInline() {
        OnboardingResponse saved = response(OnboardingStatus.PENDING);
        when(service.create(request, VerificationMode.ASYNC)).thenReturn(saved);

        var http = controller.create(request, VerificationMode.ASYNC);

        assertEquals(HttpStatus.ACCEPTED, http.getStatusCode());
        verifyNoInteractions(sync);
    }

    @Test
    void syncAttemptsInlineAndReturnsCreatedOnFinalResult() {
        OnboardingResponse saved = response(OnboardingStatus.PENDING);
        OnboardingResponse verified = response(OnboardingStatus.VERIFIED);
        when(service.create(request, VerificationMode.SYNC)).thenReturn(saved);
        when(sync.attempt(saved)).thenReturn(verified);

        var http = controller.create(request, VerificationMode.SYNC);

        assertEquals(HttpStatus.CREATED, http.getStatusCode());
        assertEquals(OnboardingStatus.VERIFIED, http.getBody().status());
        verify(sync).attempt(saved);
    }

    @Test
    void syncUnfinishedReturnsAccepted() {
        OnboardingResponse saved = response(OnboardingStatus.PENDING);
        OnboardingResponse verifying = response(OnboardingStatus.VERIFYING);
        when(service.create(request, VerificationMode.SYNC)).thenReturn(saved);
        when(sync.attempt(saved)).thenReturn(verifying);

        assertEquals(HttpStatus.ACCEPTED,
            controller.create(request, VerificationMode.SYNC).getStatusCode());
    }

    private static OnboardingResponse response(OnboardingStatus status) {
        return new OnboardingResponse(UUID.randomUUID(), "Alex", "success@acme.com", "Acme",
            "acme.com", status, null, "test", 1, Instant.now(), Instant.now());
    }
}
