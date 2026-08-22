package com.jobseekercopilot.paymentservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jobseekercopilot.paymentservice.entity.DocumentCreditTransaction;
import com.jobseekercopilot.paymentservice.entity.DocumentCreditTransactionType;
import com.jobseekercopilot.paymentservice.entity.DocumentCreditWallet;
import com.jobseekercopilot.paymentservice.repository.DocumentCreditReservationRepository;
import com.jobseekercopilot.paymentservice.repository.DocumentCreditTransactionRepository;
import com.jobseekercopilot.paymentservice.repository.DocumentCreditWalletRepository;
import com.jobseekercopilot.paymentservice.repository.DocumentGenerationDeliveryRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentCreditLedgerReconciliationServiceTest {
    @Test
    void orphanDeliverySpendFailsReconciliation() {
        UUID walletId = UUID.randomUUID();
        DocumentCreditWalletRepository wallets = mock(DocumentCreditWalletRepository.class);
        DocumentCreditTransactionRepository transactions =
                mock(DocumentCreditTransactionRepository.class);
        DocumentCreditReservationRepository reservations =
                mock(DocumentCreditReservationRepository.class);
        DocumentGenerationDeliveryRepository deliveries =
                mock(DocumentGenerationDeliveryRepository.class);
        DocumentCreditTransaction orphanSpend = DocumentCreditTransaction.builder()
                .walletId(walletId)
                .transactionType(DocumentCreditTransactionType.DOCUMENT_SPENT)
                .balanceBefore(0)
                .balanceDeltaCredits(0)
                .balanceAfter(0)
                .operationId("DOCUMENT_DELIVERY_COMMIT:" + UUID.randomUUID())
                .build();
        when(wallets.findById(walletId)).thenReturn(Optional.of(
                DocumentCreditWallet.builder().id(walletId).balanceCredits(0).build()));
        when(transactions.findByWalletIdOrderBySequenceNumberAsc(walletId))
                .thenReturn(List.of(orphanSpend));
        when(transactions.sumBalanceDeltaByWalletId(walletId)).thenReturn(0L);
        when(reservations.findByWalletId(walletId)).thenReturn(List.of());

        DocumentCreditLedgerReconciliationService.Result result =
                new DocumentCreditLedgerReconciliationService(
                        wallets, transactions, reservations, deliveries).reconcile(walletId);

        assertThat(result.reservationsValid()).isFalse();
        assertThat(result.reconciled()).isFalse();
    }
}
