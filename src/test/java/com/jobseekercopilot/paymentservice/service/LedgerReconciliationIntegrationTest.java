package com.jobseekercopilot.paymentservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobseekercopilot.paymentservice.dto.CommitReservationRequest;
import com.jobseekercopilot.paymentservice.dto.CreateReservationRequest;
import com.jobseekercopilot.paymentservice.entity.AiTokenTransaction;
import com.jobseekercopilot.paymentservice.entity.AiTokenWallet;
import com.jobseekercopilot.paymentservice.repository.AiTokenReservationRepository;
import com.jobseekercopilot.paymentservice.repository.AiTokenTransactionRepository;
import com.jobseekercopilot.paymentservice.repository.AiTokenWalletRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class LedgerReconciliationIntegrationTest {
    @Autowired private PaymentService paymentService;
    @Autowired private LedgerReconciliationService reconciliationService;
    @Autowired private AiTokenWalletRepository walletRepository;
    @Autowired private AiTokenTransactionRepository transactionRepository;
    @Autowired private AiTokenReservationRepository reservationRepository;

    @BeforeEach
    void clean() {
        reservationRepository.deleteAll();
        transactionRepository.deleteAll();
        walletRepository.deleteAll();
    }

    @Test
    void reconstructsWalletFromUnambiguousReservationSpendAndReleaseEntries() {
        String userId = "ledger-owner";
        paymentService.wallet(userId);

        CreateReservationRequest create = new CreateReservationRequest();
        create.setFeature("CV_AND_COVER_LETTER_GENERATION");
        create.setEstimatedTokens(10_000);
        create.setOperationKey("ledger-reconciliation");
        create.setReferenceType("JOB_APPLICATION");
        create.setReferenceId("job-123");
        var reservation = paymentService.createReservation(userId, create);

        CommitReservationRequest commit = new CommitReservationRequest();
        commit.setActualTokens(7_300L);
        paymentService.commitReservation(userId, reservation.getReservationId(), commit);

        AiTokenWallet wallet = walletRepository.findByUserId(userId).orElseThrow();
        List<AiTokenTransaction> entries =
                transactionRepository.findByWalletIdOrderBySequenceNumberAsc(wallet.getId());

        assertThat(entries)
                .extracting(AiTokenTransaction::getBalanceDeltaTokens)
                .containsExactly(20_000L, -10_000L, 0L, 2_700L);
        assertThat(entries)
                .extracting(AiTokenTransaction::getBalanceBefore)
                .containsExactly(0L, 20_000L, 10_000L, 10_000L);
        assertThat(entries)
                .extracting(AiTokenTransaction::getBalanceAfter)
                .containsExactly(20_000L, 10_000L, 10_000L, 12_700L);
        assertThat(entries)
                .extracting(AiTokenTransaction::getOperationId)
                .allSatisfy(operationId -> assertThat(operationId).isNotBlank());
        assertThat(entries)
                .extracting(AiTokenTransaction::getSequenceNumber)
                .isSorted()
                .doesNotHaveDuplicates();

        LedgerReconciliationResult result = reconciliationService.reconcile(wallet.getId());
        assertThat(result.reconciled()).isTrue();
        assertThat(result.storedBalanceTokens()).isEqualTo(12_700);
        assertThat(result.reconstructedBalanceTokens()).isEqualTo(12_700);
        assertThat(result.ledgerEntryCount()).isEqualTo(4);
    }

    @Test
    void failsClosedWhenStoredWalletBalanceDivergesFromLedger() {
        String userId = "divergent-owner";
        paymentService.wallet(userId);
        AiTokenWallet wallet = walletRepository.findByUserId(userId).orElseThrow();
        wallet.setBalanceTokens(wallet.getBalanceTokens() + 1);
        walletRepository.saveAndFlush(wallet);

        assertThat(reconciliationService.reconcile(wallet.getId()).reconciled()).isFalse();
        assertThatThrownBy(reconciliationService::verifyAllOrThrow)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AI Credit ledger reconciliation failed for 1 wallet(s)");
    }
}
