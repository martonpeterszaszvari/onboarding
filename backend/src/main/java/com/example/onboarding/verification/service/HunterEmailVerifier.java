package com.example.onboarding.verification.service;

import com.example.onboarding.verification.data.VerificationResult;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Optional real integration. Activate with HUNTER_MODE=real and HUNTER_API_KEY=... */
@Component
@ConditionalOnProperty(name = "hunter.mode", havingValue = "real")
public class HunterEmailVerifier implements EmailVerifier {
    private final RestClient client;
    private final String apiKey;

    public HunterEmailVerifier(@Value("${hunter.api-key:}") String apiKey) {
        if (apiKey.isBlank()) throw new IllegalStateException("HUNTER_API_KEY is required in real mode");
        this.apiKey = apiKey;
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(25));
        this.client = RestClient.builder().baseUrl("https://api.hunter.io")
            .requestFactory(factory).build();
    }

    // Only fields used from Hunter's v2 response: {"data":{"status":"valid"}}.
    public record HunterResponse(HunterData data) { }
    public record HunterData(String status) { }

    @Override
    public VerificationResult verify(String email, int attempt) {
        try {
            ResponseEntity<HunterResponse> response = client.get()
                .uri(builder -> builder.path("/v2/email-verifier")
                    .queryParam("email", email).queryParam("api_key", apiKey).build())
                .retrieve().toEntity(HunterResponse.class);
            int http = response.getStatusCode().value();
            if (http == 202) return VerificationResult.pending();
            if (http == 222) return VerificationResult.retryable("smtp_error");
            if (http != 200 || response.getBody() == null || response.getBody().data() == null)
                return VerificationResult.retryable("unexpected_response");
            String status = response.getBody().data().status();
            if (status == null) return VerificationResult.retryable("missing_status");
            return switch (status) {
                case "valid" -> VerificationResult.valid();
                case "invalid" -> VerificationResult.invalid();
                // These are NOT proofs of identity or company authorization.
                case "accept_all", "webmail", "disposable", "unknown" -> VerificationResult.inconclusive(status);
                default -> VerificationResult.inconclusive("unexpected_status");
            };
        } catch (RestClientResponseException exception) {
            int http = exception.getStatusCode().value();
            return switch (http) {
                case 400 -> VerificationResult.invalid();
                case 403 -> VerificationResult.retryable("rate_limited");
                case 429 -> VerificationResult.unavailable("quota_exceeded");
                case 401 -> VerificationResult.unavailable("authentication_failed");
                case 451 -> VerificationResult.inconclusive("privacy_restricted");
                default -> http >= 500 ? VerificationResult.retryable("hunter_5xx")
                    : VerificationResult.unavailable("hunter_http_" + http);
            };
        } catch (ResourceAccessException exception) {
            return VerificationResult.retryable("network_timeout");
        }
    }
}
