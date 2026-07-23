package com.jobseekercopilot.paymentservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EstimateResponse {
    private String feature;
    private long estimatedTotalTokens;
    private long estimatedCostTokens;
    private long userBalanceTokens;
    private boolean canAfford;
}
