package com.jobseekercopilot.paymentservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CheckoutReadinessResponse {
    private boolean checkoutAvailable;
    private String code;
    private String mode;
}
