package com.jobseekercopilot.paymentservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "document_generation_deliveries",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_document_generation_delivery_document",
                    columnNames = "generated_document_id"),
            @UniqueConstraint(
                    name = "uk_document_generation_delivery_reservation_type",
                    columnNames = {"reservation_id", "document_type"})
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentGenerationDelivery {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private UUID reservationId;
    @Column(nullable = false)
    private UUID walletId;
    @Column(nullable = false, length = 128)
    private String userId;
    @Column(name = "generated_document_id", nullable = false)
    private UUID generatedDocumentId;
    @Column(name = "document_type", nullable = false, length = 32)
    private String documentType;
    @Column(nullable = false)
    private boolean regeneration;
    @Column(nullable = false, updatable = false)
    private Instant deliveredAt;

    @PrePersist
    void createTimestamp() {
        if (deliveredAt == null) deliveredAt = Instant.now();
    }
}
