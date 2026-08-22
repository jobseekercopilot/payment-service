package com.jobseekercopilot.paymentservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BindCheckoutSessionRequest {
    @NotBlank
    private String stripeSessionId;
}
