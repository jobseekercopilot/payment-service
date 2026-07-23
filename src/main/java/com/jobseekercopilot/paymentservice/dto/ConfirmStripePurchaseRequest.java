package com.jobseekercopilot.paymentservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConfirmStripePurchaseRequest {
    @NotBlank
    private String userId;

    @NotBlank
    private String pricingPlanId;

    @Min(1)
    private long tokenAmount;

    @NotBlank
    private String stripeSessionId;

    private String stripePaymentIntentId;
}
