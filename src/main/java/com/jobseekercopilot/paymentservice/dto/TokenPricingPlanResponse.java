package com.jobseekercopilot.paymentservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TokenPricingPlanResponse {
    private String id;
    private String name;
    private String description;
    private long tokenAmount;
    private long priceGbpPence;
}
