package com.jobseekercopilot.paymentservice.service;

import com.jobseekercopilot.paymentservice.config.PaymentProperties;
import com.jobseekercopilot.paymentservice.dto.ProviderCheckoutSessionReference;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProviderSessionExpiryReconciliationService {
    private final PaymentOrderService paymentOrderService;
    private final StripeLifecycleClient stripeLifecycleClient;
    private final PaymentProperties properties;

    public boolean reconcile(
            String owner, ProviderCheckoutSessionReference reference) {
        if (!stripeLifecycleClient.expire(owner, reference)) {
            return false;
        }
        paymentOrderService.confirmProviderSessionExpired(owner, reference);
        return true;
    }

    public void reconcile(String owner, List<ProviderCheckoutSessionReference> references) {
        for (ProviderCheckoutSessionReference reference : references) {
            reconcile(owner, reference);
        }
    }

    @Scheduled(
            fixedDelayString =
                    "${payment.account-lifecycle.provider-session-recovery-interval:PT30S}")
    public void recoverPendingProviderSessions() {
        PaymentProperties.AccountLifecycle lifecycle = properties.getAccountLifecycle();
        if (!lifecycle.isProviderSessionRecoveryEnabled()) {
            return;
        }
        List<UUID> ids = paymentOrderService.pendingProviderSessionExpiryIds(
                lifecycle.getProviderSessionRecoveryBatchSize());
        for (UUID id : ids) {
            PaymentOrderService.PendingProviderSession pending =
                    paymentOrderService.pendingProviderSessionExpiry(id);
            if (pending == null) {
                continue;
            }
            try {
                reconcile(pending.owner(), pending.reference());
            } catch (RuntimeException failure) {
                log.warn("Provider Checkout expiry reconciliation will be retried orderId={} error={}",
                        id, failure.getClass().getSimpleName());
            }
        }
        List<UUID> expiredOpenIds = paymentOrderService.expiredOpenProviderSessionIds(
                Instant.now(), lifecycle.getProviderSessionRecoveryBatchSize());
        for (UUID id : expiredOpenIds) {
            PaymentOrderService.PendingProviderSession pending =
                    paymentOrderService.openProviderSessionExpiry(id);
            if (pending == null) {
                continue;
            }
            try {
                reconcile(pending.owner(), pending.reference());
            } catch (RuntimeException failure) {
                log.warn("Open provider Checkout reconciliation will be retried orderId={} error={}",
                        id, failure.getClass().getSimpleName());
            }
        }
    }
}
