package com.jobseekercopilot.paymentservice.dto;

import com.jobseekercopilot.paymentservice.entity.TransactionType;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransactionResponse {
    private UUID id;
    private TransactionType transactionType;
    private long tokenAmount;
    private long balanceDeltaTokens;
    private long balanceBefore;
    private long balanceAfter;
    private String operationId;
    private String description;
    private String referenceType;
    private String referenceId;
    private Instant createdAt;
}
