package com.jobseekercopilot.paymentservice.dto;

import com.jobseekercopilot.paymentservice.entity.TransactionType;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransactionResponse {
    private UUID id;
    private TransactionType transactionType;
    private long tokenAmount;
    private long balanceBefore;
    private long balanceAfter;
    private String description;
    private String referenceType;
    private String referenceId;
    private LocalDateTime createdAt;
}
