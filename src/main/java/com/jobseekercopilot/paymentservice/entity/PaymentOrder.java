package com.jobseekercopilot.paymentservice.entity;

import com.jobseekercopilot.paymentservice.config.PaymentProperties;
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
@Table(name = "payment_orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, length = 128)
    private String userId;
    @Column(nullable = false, length = 128)
    private String idempotencyKey;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PaymentOrderStatus status;
    @Column(nullable = false, length = 64)
    private String catalogVersion;
    @Column(nullable = false, length = 64)
    private String pricingPlanId;
    @Column(nullable = false, length = 128)
    private String pricingPlanName;
    @Column(nullable = false)
    private int baseDocumentCredits;
    @Column(nullable = false)
    private int promotionBonusCredits;
    @Column(nullable = false)
    private long priceMinor;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(nullable = false, length = 2)
    private String billingCountryIntent;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentProperties.TaxTreatment taxTreatment;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentProperties.TaxStatus taxStatus;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentProperties.LegalEntityType legalEntityType;
    @Column(nullable = false, length = 64)
    private String legalEntityConfigurationVersion;
    @Column(nullable = false, length = 64)
    private String consumerTermsVersion;
    @Column(nullable = false)
    private boolean immediateSupplyRequested;
    @Column(nullable = false)
    private boolean cancellationRightLossAcknowledged;
    @Column(nullable = false)
    private Instant consumerTermsAcceptedAt;
    @Column(unique = true, length = 255)
    private String stripeSessionId;
    @Column(unique = true, length = 255)
    private String stripePaymentIntentId;
    private Boolean providerLivemode;
    @Column(length = 2)
    private String providerBillingCountry;
    private Long providerAmountTotalMinor;
    @Column(length = 3)
    private String providerCurrency;
    @Column(nullable = false)
    private int reversedDocumentCredits;
    @Column(nullable = false)
    private int reviewShortfallCredits;
    @Column(length = 128)
    private String manualReviewReason;
    @Version
    @Column(nullable = false)
    private long version;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant expiresAt;
    private Instant checkoutBoundAt;
    private Instant paidAt;
    private Instant fulfilledAt;
    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void createTimestamps() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }
}
