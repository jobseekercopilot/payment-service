package com.jobseekercopilot.paymentservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "document_credit_wallets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentCreditWallet {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 128)
    private String userId;

    @Column(nullable = false)
    private int balanceCredits;
    @Column(nullable = false)
    private int lifetimePurchasedCredits;
    @Column(nullable = false)
    private int lifetimeSpentCredits;
    @Column(nullable = false)
    private int lifetimeReversedCredits;
    @Column(nullable = false)
    private boolean freeAllowanceGranted;
    private Long migratedLegacyBalanceTokens;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DocumentCreditWalletStatus lifecycleStatus;

    @Column(nullable = false)
    private int reviewDebtCredits;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    private Instant revokedAt;

    @PrePersist
    void createTimestamps() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (lifecycleStatus == null) lifecycleStatus = DocumentCreditWalletStatus.ACTIVE;
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }
}
