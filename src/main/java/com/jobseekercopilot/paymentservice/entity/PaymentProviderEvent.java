package com.jobseekercopilot.paymentservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "payment_provider_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentProviderEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, length = 32)
    private String provider;
    @Column(nullable = false, length = 255)
    private String providerEventId;
    @Column(nullable = false, length = 64)
    private String eventType;
    private UUID orderId;
    @Column(nullable = false, length = 64)
    private String payloadSha256;
    @Column(length = 255)
    private String providerObjectId;
    private Long amountMinor;
    @Column(length = 3)
    private String currency;
    @Column(length = 2)
    private String billingCountry;
    private Boolean providerLivemode;
    private Instant providerCreatedAt;
    @Column(nullable = false, length = 40)
    private String outcome;
    @Column(nullable = false, updatable = false)
    private Instant receivedAt;
    private Instant processedAt;

    @PrePersist
    void createTimestamp() {
        if (receivedAt == null) receivedAt = Instant.now();
    }
}
