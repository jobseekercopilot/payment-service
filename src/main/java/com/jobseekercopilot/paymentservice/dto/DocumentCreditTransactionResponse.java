package com.jobseekercopilot.paymentservice.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DocumentCreditTransactionResponse {
    private UUID id;
    private Type type;
    private int documentCredits;
    private int balanceBeforeDocumentCredits;
    private int balanceAfterDocumentCredits;
    private String operationId;
    private String description;
    private String referenceType;
    private String referenceId;
    private Instant createdAt;

    public enum Type {
        FREE_ALLOWANCE_GRANTED,
        PURCHASE,
        PROMOTION_BONUS,
        DOCUMENT_RESERVED,
        DOCUMENT_SPENT,
        DOCUMENT_RESERVATION_RELEASED,
        REFUND_REVERSAL,
        DISPUTE_REVERSAL,
        ADJUSTMENT
    }
}
