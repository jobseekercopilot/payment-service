package com.jobseekercopilot.paymentservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Data
public class ProviderPaymentEventRequest {
    @NotBlank @Size(max = 255) private String providerEventId;
    @NotBlank @Size(max = 64) private String eventType;
    @NotBlank
    @Pattern(regexp = "[a-f0-9]{64}")
    private String payloadSha256;
    private UUID orderId;
    @Size(max = 255)
    private String stripeSessionId;
    @Size(max = 255)
    private String paymentIntentId;
    @Size(max = 40)
    private String paymentStatus;
    @Size(max = 40)
    private String checkoutStatus;
    @Pattern(regexp = "[A-Za-z]{3}")
    private String currency;
    @Min(0) private Long amountTotalMinor;
    @Pattern(regexp = "[A-Za-z]{2}")
    private String billingCountry;
    @NotNull private Boolean liveMode;
    private Instant eventCreatedAt;
    @Min(0) private Long reversalAmountMinor;
}
