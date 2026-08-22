package com.jobseekercopilot.paymentservice.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DocumentCreditReservationResponse {
    private UUID reservationId;
    private int documentCredits;
    private int balanceAfterReservation;
    private String status;
    private String operationKey;
    private boolean regeneration;
    private Instant expiresAt;
}
