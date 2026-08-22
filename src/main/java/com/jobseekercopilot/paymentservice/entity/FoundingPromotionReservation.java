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
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "founding_promotion_reservations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoundingPromotionReservation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, length = 64)
    private String campaignId;
    @Column(nullable = false, unique = true)
    private UUID orderId;
    @Column(nullable = false, length = 128)
    private String userId;
    @Column(nullable = false)
    private int bonusDocumentCredits;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PromotionReservationStatus status;
    @Column(unique = true, length = 128)
    private String completedOwnerKey;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant expiresAt;
    private Instant completedAt;
    private Instant releasedAt;

    @PrePersist
    void createTimestamp() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
