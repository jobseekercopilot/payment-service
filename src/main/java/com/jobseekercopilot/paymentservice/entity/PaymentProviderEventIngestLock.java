package com.jobseekercopilot.paymentservice.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Database-owned serialization point for provider event IDs. This prevents two
 * service instances from racing the provider-event unique constraint before
 * either transaction can observe the other's durable replay evidence.
 */
@Entity
@Table(name = "payment_provider_event_ingest_locks")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentProviderEventIngestLock {
    @Id
    private Integer id;
}
