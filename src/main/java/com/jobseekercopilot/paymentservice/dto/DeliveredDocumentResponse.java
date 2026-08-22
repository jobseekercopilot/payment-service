package com.jobseekercopilot.paymentservice.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeliveredDocumentResponse {
    private UUID documentId;
    private String documentType;
    private boolean regeneration;
    private Instant deliveredAt;
}
