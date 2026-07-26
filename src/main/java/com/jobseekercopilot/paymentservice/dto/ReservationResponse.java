package com.jobseekercopilot.paymentservice.dto;

import com.jobseekercopilot.paymentservice.entity.ReservationStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReservationResponse {
    private UUID reservationId;
    private String userId;
    private long reservedTokens;
    private long balanceAfterReservation;
    private ReservationStatus status;
    private String operationKey;
    private Instant expiresAt;
}
