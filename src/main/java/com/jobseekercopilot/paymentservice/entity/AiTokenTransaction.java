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
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "ai_token_transactions")
@Immutable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiTokenTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, insertable = false, updatable = false)
    private long sequenceNumber;

    @Column(nullable = false, updatable = false, length = 128)
    private String userId;

    @Column(nullable = false, updatable = false)
    private UUID walletId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private TransactionType transactionType;

    @Column(nullable = false, updatable = false)
    private long tokenAmount;

    @Column(nullable = false, updatable = false)
    private long balanceDeltaTokens;

    @Column(nullable = false, updatable = false)
    private long balanceBefore;

    @Column(nullable = false, updatable = false)
    private long balanceAfter;

    @Column(nullable = false, updatable = false, length = 255)
    private String operationId;

    @Column(updatable = false, length = 512)
    private String description;

    @Column(updatable = false, length = 64)
    private String referenceType;

    @Column(updatable = false, length = 255)
    private String referenceId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
