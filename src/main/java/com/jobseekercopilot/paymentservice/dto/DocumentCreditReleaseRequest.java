package com.jobseekercopilot.paymentservice.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DocumentCreditReleaseRequest {
    @Size(max = 128)
    private String reason;
}
