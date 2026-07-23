package com.jobseekercopilot.paymentservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EstimateRequest {
    @NotBlank
    private String feature;

    @Min(0)
    private long estimatedInputTokens;

    @Min(0)
    private long estimatedOutputTokens;
}
