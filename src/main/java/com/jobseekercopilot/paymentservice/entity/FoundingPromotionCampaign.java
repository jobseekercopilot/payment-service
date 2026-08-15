package com.jobseekercopilot.paymentservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "founding_promotion_campaigns")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoundingPromotionCampaign {
    @Id
    @Column(length = 64)
    private String id;
    @Column(nullable = false)
    private boolean enabled;
    @Column(nullable = false)
    private int customerLimit;
    @Column(nullable = false)
    private int activeReservations;
    @Column(nullable = false)
    private int completedClaims;
    @Version
    @Column(nullable = false)
    private long version;
    @Column(nullable = false)
    private Instant updatedAt;

    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }
}
