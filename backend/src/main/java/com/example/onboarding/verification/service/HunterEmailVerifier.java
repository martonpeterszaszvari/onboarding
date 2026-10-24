package com.example.onboarding.verification.service;


import java.net.http.HttpClient;
import java.time.Duration;

import com.example.onboarding.verification.data.VerificationResult;
import com.example.onboarding.verification.service.EmailVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
@ConditionalOnProperty(name = "hunter.mode", havingValue = "real")
public class HunterEmailVerifier implements EmailVerifier {

    private final RestClient fastClient;
    private final RestClient backgroundClient;

    public HunterEmailVerifier(
            @Value("${hunter.api-key:}") String apiKey
    ) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException(
                    "HUNTER_API_KEY is required in real mode"
            );
        }

        // Short timeout for the original HTTP request.
        this.fastClient = createClient(
                apiKey,
                Duration.ofSeconds(1),
                Duration.ofSeconds(2)
        );

        // Longer timeout for background verification.
        this.backgroundClient = createClient(
                apiKey,
                Duration.ofSeconds(5),
                Duration.ofSeconds(25)
        );
    }

    private RestClient createClient(
            String apiKey,
            Duration connectTimeout,
            Duration readTimeout
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();

        JdkClientHttpRequestFactory factory =
                new JdkClientHttpRequestFactory(httpClient);

        factory.setReadTimeout(readTimeout);

        return RestClient.builder()
                .baseUrl("https://api.hunter.io")
                .defaultHeader("X-API-KEY", apiKey)
                .requestFactory(factory)
                .build();
    }

    public record HunterResponse(HunterData data) {}
    public record HunterData(String status) {}

    @Override
    public VerificationResult verify(
            String email,
            int attempt,
            Mode mode
    ) {
        RestClient client = mode == Mode.FAST
                ? fastClient
                : backgroundClient;

        try {
            ResponseEntity<HunterResponse> response = client.get()
                    .uri(builder -> builder
                            .path("/v2/email-verifier")
                            .queryParam("email", email)
                            .build())
                    .retrieve()
                    .toEntity(HunterResponse.class);

            int httpStatus = response.getStatusCode().value();

            if (httpStatus == 202) {
                return VerificationResult.pending();
            }

            if (httpStatus == 222) {
                return VerificationResult.retryable("smtp_error");
            }

            if (httpStatus != 200
                    || response.getBody() == null
                    || response.getBody().data() == null) {
                return VerificationResult.retryable(
                        "unexpected_response"
                );
            }

            String status = response.getBody().data().status();

            if (status == null) {
                return VerificationResult.retryable("missing_status");
            }

            return switch (status) {
                case "valid" -> VerificationResult.valid();

                case "invalid" -> VerificationResult.invalid();

                case "accept_all", "webmail",
                     "disposable", "unknown" ->
                        VerificationResult.inconclusive(status);

                default -> VerificationResult.inconclusive(
                        "unexpected_status"
                );
            };

        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();

            return switch (status) {
                case 400 -> VerificationResult.invalid();

                case 403 -> VerificationResult.retryable(
                        "rate_limited"
                );

                case 429 -> VerificationResult.unavailable(
                        "quota_exceeded"
                );

                case 401 -> VerificationResult.unavailable(
                        "authentication_failed"
                );

                case 451 -> VerificationResult.inconclusive(
                        "privacy_restricted"
                );

                default -> status >= 500
                        ? VerificationResult.retryable("hunter_5xx")
                        : VerificationResult.unavailable(
                        "hunter_http_" + status
                );
            };

        } catch (ResourceAccessException exception) {
            // The provider did not respond within our
            // configured HTTP timeout, or a network error occurred.
            // The job manager will schedule another attempt.
            return VerificationResult.retryable("network_timeout");
        }
    }
}
