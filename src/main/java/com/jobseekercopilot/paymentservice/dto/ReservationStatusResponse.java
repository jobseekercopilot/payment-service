package com.jobseekercopilot.paymentservice.dto;

import com.jobseekercopilot.paymentservice.entity.ReservationStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReservationStatusResponse {
    private UUID reservationId;
    private String userId;
    private String operationKey;
    private String feature;
    private long reservedTokens;
    private Long committedTokens;
    private Long releasedTokens;
    private ReservationStatus status;
    private String referenceType;
    private String referenceId;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant committedAt;
    private Instant releasedAt;
    private Instant lastTransitionAt;
    private String lastTransitionReason;
    private int reconciliationAttempts;
    private Instant lastReconciliationAttemptAt;
    private String reconciliationErrorCode;
}
