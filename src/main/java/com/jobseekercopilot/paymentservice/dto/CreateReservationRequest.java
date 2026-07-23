package com.jobseekercopilot.paymentservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateReservationRequest {
    @NotBlank
    private String feature;

    @Min(1)
    private long estimatedTokens;

    private String referenceType;

    private String referenceId;
}
