package com.jobseekercopilot.paymentservice.service;

import com.jobseekercopilot.paymentservice.config.PaymentProperties;
import com.jobseekercopilot.paymentservice.entity.ReservationStatus;
import com.jobseekercopilot.paymentservice.repository.AiTokenReservationRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReservationRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(ReservationRecoveryService.class);

    private final AiTokenReservationRepository reservationRepository;
    private final PaymentService paymentService;
    private final ReservationRecoveryFailureRecorder failureRecorder;
    private final PaymentProperties paymentProperties;

    @Scheduled(fixedDelayString = "${payment.reservation-recovery.interval:PT30S}")
    public void reconcileExpiredReservations() {
        if (!paymentProperties.getReservationRecovery().isEnabled()) {
            return;
        }
        reconcileExpiredReservations(Instant.now());
    }

    public int reconcileExpiredReservations(Instant reconciliationTime) {
        int batchSize = paymentProperties.getReservationRecovery().getBatchSize();
        if (batchSize < 1) {
            throw new IllegalStateException(
                    "Payment reservation recovery batch size must be positive");
        }
        int released = 0;
        for (UUID reservationId : reservationRepository.findExpiredIds(
                ReservationStatus.RESERVED,
                reconciliationTime,
                PageRequest.of(0, batchSize))) {
            try {
                if (paymentService.reconcileExpiredReservation(
                        reservationId, reconciliationTime)) {
                    released++;
                }
            } catch (RuntimeException failure) {
                log.error(
                        "Expired reservation recovery failed reservationId={} error={}",
                        reservationId,
                        failure.getClass().getSimpleName());
                failureRecorder.record(reservationId, failure);
            }
        }
        if (released > 0) {
            log.info("Expired reservation recovery completed released={}", released);
        }
        return released;
    }
}
