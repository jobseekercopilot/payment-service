package com.jobseekercopilot.paymentservice.entity;

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
@Table(name = "document_credit_reservations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentCreditReservation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, length = 128)
    private String userId;
    @Column(nullable = false)
    private UUID walletId;
    @Column(nullable = false, length = 200)
    private String operationKey;
    @Column(nullable = false)
    private int documentCredits;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DocumentCreditReservationStatus status;
    @Column(nullable = false)
    private boolean regeneration;
    @Column(length = 64)
    private String referenceType;
    @Column(length = 255)
    private String referenceId;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant expiresAt;
    private Instant committedAt;
    private Instant releasedAt;
    @Column(nullable = false, length = 128)
    private String lastTransitionReason;
    @Version
    @Column(nullable = false)
    private long version;

    @PrePersist
    void createTimestamp() {
        if (createdAt == null) createdAt = Instant.now();
        if (lastTransitionReason == null) lastTransitionReason = "CREATED";
    }
}
