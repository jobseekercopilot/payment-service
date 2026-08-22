package com.jobseekercopilot.paymentservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobseekercopilot.paymentservice.config.PaymentProperties;
import com.jobseekercopilot.paymentservice.entity.ReservationStatus;
import com.jobseekercopilot.paymentservice.repository.AiTokenReservationRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class ReservationRecoveryServiceTest {
    @Test
    void failedRecoveryIsFlaggedAndDoesNotAbortTheBatch() {
        AiTokenReservationRepository repository =
                mock(AiTokenReservationRepository.class);
        PaymentService paymentService = mock(PaymentService.class);
        ReservationRecoveryFailureRecorder failureRecorder =
                mock(ReservationRecoveryFailureRecorder.class);
        PaymentProperties properties = new PaymentProperties();
        UUID failedId = UUID.randomUUID();
        UUID releasedId = UUID.randomUUID();
        Instant reconciliationTime = Instant.parse("2026-07-26T00:00:00Z");
        IllegalStateException failure = new IllegalStateException("database unavailable");
        when(repository.findExpiredIds(
                        eq(ReservationStatus.RESERVED),
                        eq(reconciliationTime),
                        any(Pageable.class)))
                .thenReturn(List.of(failedId, releasedId));
        when(paymentService.reconcileExpiredReservation(failedId, reconciliationTime))
                .thenThrow(failure);
        when(paymentService.reconcileExpiredReservation(releasedId, reconciliationTime))
                .thenReturn(true);
        ReservationRecoveryService service = new ReservationRecoveryService(
                repository, paymentService, failureRecorder, properties);

        int released = service.reconcileExpiredReservations(reconciliationTime);

        assertThat(released).isEqualTo(1);
        verify(failureRecorder).record(failedId, failure);
    }
}
