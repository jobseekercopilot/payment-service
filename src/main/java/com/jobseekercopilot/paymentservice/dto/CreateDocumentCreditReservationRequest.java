package com.jobseekercopilot.paymentservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateDocumentCreditReservationRequest {
    @NotBlank
    @Size(max = 200)
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{0,199}")
    private String operationKey;
    @Min(1)
    @Max(2)
    private int documentCredits;
    private boolean regeneration;
    @Size(max = 64)
    private String referenceType;
    @Size(max = 255)
    private String referenceId;
}
