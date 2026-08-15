package com.jobseekercopilot.paymentservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreatePaymentOrderRequest {
    @NotBlank
    private String pricingPlanId;
    @NotBlank
    @Pattern(regexp = "GB")
    private String billingCountry;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = "true")
    private Boolean immediateSupplyRequested;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = "true")
    private Boolean cancellationRightLossAcknowledged;
}
