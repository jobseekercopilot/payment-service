package com.jobseekercopilot.paymentservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data
public class CommitDocumentGenerationRequest {
    @NotNull
    @Size(min = 1, max = 2)
    private List<@Valid DeliveredDocument> deliveries;

    @Data
    public static class DeliveredDocument {
        @NotNull
        private UUID documentId;
        @NotNull
        private DocumentType documentType;
    }

    public enum DocumentType {
        CV,
        COVER_LETTER
    }
}
