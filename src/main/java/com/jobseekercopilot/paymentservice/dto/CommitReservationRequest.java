package com.jobseekercopilot.paymentservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CommitReservationRequest {
    @NotNull
    @Min(0)
    private Long actualTokens;

    private String provider;

    private String model;

    private Long inputTokens;

    private Long outputTokens;

    private String description;
}
