package com.jobseekercopilot.paymentservice.dto;

import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AccountPaymentLifecycleResponse {
    private String status;
    private Instant accessRevokedAt;
    private int financialRecordRetentionYears;
    private boolean providerReconciliationEvidenceRetained;
    private int checkoutOrdersRevoked;
    /**
     * Compatibility signal for lifecycle coordinators. A successful response
     * always reports false because Payment Service completes provider cleanup
     * before returning HTTP 200.
     */
    private boolean providerSessionsRequireExpiry;
    /**
     * Compatibility field retained as an always-empty list. Provider session
     * identifiers never delegate Stripe access to the lifecycle coordinator.
     */
    private List<ProviderCheckoutSessionReference> providerCheckoutSessionsToExpire;
}
