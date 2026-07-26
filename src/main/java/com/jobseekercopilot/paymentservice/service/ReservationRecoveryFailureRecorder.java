package com.jobseekercopilot.paymentservice.service;

import com.jobseekercopilot.paymentservice.entity.AiTokenReservation;
import com.jobseekercopilot.paymentservice.entity.ReservationStatus;
import com.jobseekercopilot.paymentservice.repository.AiTokenReservationRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReservationRecoveryFailureRecorder {
    private final AiTokenReservationRepository reservationRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID reservationId, RuntimeException failure) {
        AiTokenReservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElse(null);
        if (reservation == null || reservation.getStatus() != ReservationStatus.RESERVED) {
            return;
        }
        reservation.setReconciliationAttempts(
                Math.addExact(reservation.getReconciliationAttempts(), 1));
        reservation.setLastReconciliationAttemptAt(Instant.now());
        reservation.setReconciliationErrorCode(errorCode(failure));
        reservationRepository.save(reservation);
    }

    private String errorCode(RuntimeException failure) {
        String simpleName = failure.getClass().getSimpleName();
        return simpleName == null || simpleName.isBlank()
                ? "RESERVATION_RECOVERY_FAILED"
                : simpleName.substring(0, Math.min(simpleName.length(), 128));
    }
}
