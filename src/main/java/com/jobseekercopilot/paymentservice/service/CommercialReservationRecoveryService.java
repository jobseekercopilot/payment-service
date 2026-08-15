package com.jobseekercopilot.paymentservice.service;

import com.jobseekercopilot.paymentservice.config.PaymentProperties;
import com.jobseekercopilot.paymentservice.entity.DocumentCreditReservationStatus;
import com.jobseekercopilot.paymentservice.entity.PaymentOrderStatus;
import com.jobseekercopilot.paymentservice.repository.DocumentCreditReservationRepository;
import com.jobseekercopilot.paymentservice.repository.PaymentOrderRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommercialReservationRecoveryService {
    private static final Logger log =
            LoggerFactory.getLogger(CommercialReservationRecoveryService.class);
    private static final List<PaymentOrderStatus> EXPIRABLE_ORDER_STATUSES = List.of(
            PaymentOrderStatus.PENDING_CHECKOUT);

    private final DocumentCreditReservationRepository reservationRepository;
    private final PaymentOrderRepository orderRepository;
    private final DocumentCreditService documentCreditService;
    private final PaymentOrderService paymentOrderService;
    private final PaymentProperties properties;

    @Scheduled(fixedDelayString = "${payment.reservation-recovery.interval:PT30S}")
    public void reconcileExpiredCommercialReservations() {
        if (!properties.getReservationRecovery().isEnabled()) return;
        reconcileExpiredCommercialReservations(Instant.now());
    }

    public RecoveryResult reconcileExpiredCommercialReservations(Instant reconciliationTime) {
        int batchSize = properties.getReservationRecovery().getBatchSize();
        if (batchSize < 1) {
            throw new IllegalStateException("Payment reservation recovery batch size must be positive");
        }
        int releasedCredits = 0;
        int expiredOrders = 0;
        for (UUID id : reservationRepository.findExpiredIds(
                DocumentCreditReservationStatus.RESERVED,
                reconciliationTime,
                PageRequest.of(0, batchSize))) {
            try {
                if (documentCreditService.reconcileExpiredReservation(id, reconciliationTime)) {
                    releasedCredits++;
                }
            } catch (RuntimeException failure) {
                log.error("Document-credit reservation recovery failed reservationId={} error={}",
                        id, failure.getClass().getSimpleName());
            }
        }
        for (UUID id : orderRepository.findExpiredIds(
                EXPIRABLE_ORDER_STATUSES,
                reconciliationTime,
                PageRequest.of(0, batchSize))) {
            try {
                if (paymentOrderService.reconcileExpiredOrder(id, reconciliationTime)) {
                    expiredOrders++;
                }
            } catch (RuntimeException failure) {
                log.error("Checkout-order recovery failed orderId={} error={}",
                        id, failure.getClass().getSimpleName());
            }
        }
        if (releasedCredits > 0 || expiredOrders > 0) {
            log.info("Commercial reservation recovery completed releasedCredits={} expiredOrders={}",
                    releasedCredits, expiredOrders);
        }
        return new RecoveryResult(releasedCredits, expiredOrders);
    }

    public record RecoveryResult(int releasedDocumentReservations, int expiredPaymentOrders) {}
}
