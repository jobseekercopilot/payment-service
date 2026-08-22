package com.jobseekercopilot.paymentservice.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ai_token_reservations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiTokenReservation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 128)
    private String userId;

    @Column(nullable = false)
    private UUID walletId;

    @Column(nullable = false, length = 128)
    private String feature;

    @Column(nullable = false, length = 200)
    private String operationKey;

    @Column(nullable = false)
    private long reservedTokens;

    private Long committedTokens;

    private Long releasedTokens;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(length = 64)
    private String referenceType;

    @Column(length = 255)
    private String referenceId;

    @Version
    @Column(nullable = false)
    @JsonIgnore
    @Schema(hidden = true)
    private long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant committedAt;

    private Instant releasedAt;

    @Column(nullable = false)
    private Instant lastTransitionAt;

    @Column(nullable = false, length = 128)
    private String lastTransitionReason;

    @Column(nullable = false)
    private int reconciliationAttempts;

    private Instant lastReconciliationAttemptAt;

    @Column(length = 128)
    private String reconciliationErrorCode;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (lastTransitionAt == null) {
            lastTransitionAt = createdAt;
        }
        if (lastTransitionReason == null) {
            lastTransitionReason = "CREATED";
        }
    }
}
