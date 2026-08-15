package com.jobseekercopilot.paymentservice.service;

import com.jobseekercopilot.paymentservice.config.PaymentProperties;
import com.jobseekercopilot.paymentservice.dto.ProviderCheckoutSessionReference;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@Slf4j
public class StripeLifecycleClient {
    private static final String SERVICE_TOKEN = "X-Service-Token";
    private static final String OWNER = "X-Payment-Owner";

    private final RestClient stripeGateway;
    private final PaymentProperties properties;

    public StripeLifecycleClient(RestClient.Builder builder, PaymentProperties properties) {
        this.properties = properties;
        this.stripeGateway = builder
                .baseUrl(properties.getAccountLifecycle().getStripeGatewayUrl())
                .build();
    }

    public boolean expire(
            String owner, ProviderCheckoutSessionReference reference) {
        String token = properties.getAccountLifecycle().getStripeGatewayToken();
        if (token == null || token.isBlank()) {
            log.error("Provider Checkout expiry is not configured orderId={}", reference.orderId());
            return false;
        }
        try {
            StripeExpiryResponse response = stripeGateway.post()
                    .uri("/internal/v2/stripe/checkout-sessions/expire")
                    .header(SERVICE_TOKEN, token)
                    .header(OWNER, owner)
                    .body(reference)
                    .retrieve()
                    .body(StripeExpiryResponse.class);
            return response != null
                    && reference.orderId().equals(response.orderId())
                    && reference.providerSessionId().equals(response.providerSessionId())
                    && "EXPIRED".equals(response.status());
        } catch (RestClientException failure) {
            log.warn("Provider Checkout expiry will be retried orderId={} error={}",
                    reference.orderId(), failure.getClass().getSimpleName());
            return false;
        }
    }

    public record StripeExpiryResponse(
            UUID orderId, String providerSessionId, String status, String paymentStatus) {}
}
