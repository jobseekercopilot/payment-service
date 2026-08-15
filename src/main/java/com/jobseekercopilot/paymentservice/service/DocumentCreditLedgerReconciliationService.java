package com.jobseekercopilot.paymentservice.service;

import com.jobseekercopilot.paymentservice.entity.DocumentCreditReservation;
import com.jobseekercopilot.paymentservice.entity.DocumentCreditReservationStatus;
import com.jobseekercopilot.paymentservice.entity.DocumentCreditTransaction;
import com.jobseekercopilot.paymentservice.entity.DocumentCreditTransactionType;
import com.jobseekercopilot.paymentservice.entity.DocumentCreditWallet;
import com.jobseekercopilot.paymentservice.repository.DocumentCreditReservationRepository;
import com.jobseekercopilot.paymentservice.repository.DocumentCreditTransactionRepository;
import com.jobseekercopilot.paymentservice.repository.DocumentCreditWalletRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentCreditLedgerReconciliationService {
    private final DocumentCreditWalletRepository walletRepository;
    private final DocumentCreditTransactionRepository transactionRepository;
    private final DocumentCreditReservationRepository reservationRepository;

    @Transactional(readOnly = true)
    public Result reconcile(UUID walletId) {
        DocumentCreditWallet wallet = walletRepository.findById(walletId).orElseThrow();
        List<DocumentCreditTransaction> entries =
                transactionRepository.findByWalletIdOrderBySequenceNumberAsc(walletId);
        int expectedBefore = 0;
        boolean chainValid = true;
        for (DocumentCreditTransaction entry : entries) {
            if (entry.getBalanceBefore() != expectedBefore
                    || entry.getBalanceAfter()
                    != Math.addExact(entry.getBalanceBefore(), entry.getBalanceDeltaCredits())) {
                chainValid = false;
            }
            expectedBefore = entry.getBalanceAfter();
        }
        boolean reservationsValid = reservationRepository.findByWalletId(walletId).stream()
                .allMatch(reservation -> reservationEvidenceValid(reservation, entries));
        long reconstructed = transactionRepository.sumBalanceDeltaByWalletId(walletId);
        return new Result(walletId, wallet.getBalanceCredits(), reconstructed,
                entries.size(), chainValid, reservationsValid);
    }

    @Transactional(readOnly = true)
    public int verifyAllOrThrow() {
        List<Result> failures = walletRepository.findAll().stream()
                .map(wallet -> reconcile(wallet.getId()))
                .filter(result -> !result.reconciled())
                .toList();
        if (!failures.isEmpty()) {
            throw new IllegalStateException("Document-credit ledger reconciliation failed for "
                    + failures.size() + " wallet(s)");
        }
        return Math.toIntExact(walletRepository.count());
    }

    private boolean reservationEvidenceValid(
            DocumentCreditReservation reservation,
            List<DocumentCreditTransaction> entries) {
        long creates = count(entries, DocumentCreditTransactionType.DOCUMENT_RESERVED,
                "DOCUMENT_RESERVATION_CREATE:" + reservation.getOperationKey());
        long commits = count(entries, DocumentCreditTransactionType.DOCUMENT_SPENT,
                "DOCUMENT_RESERVATION_COMMIT:" + reservation.getId());
        long releases = count(entries, DocumentCreditTransactionType.DOCUMENT_RESERVATION_RELEASED,
                "DOCUMENT_RESERVATION_RELEASE:" + reservation.getId());
        if (creates != 1 || commits + releases > 1) return false;
        return switch (reservation.getStatus()) {
            case RESERVED -> commits == 0 && releases == 0;
            case COMMITTED -> commits == 1 && releases == 0;
            case RELEASED -> commits == 0 && releases == 1;
        };
    }

    private long count(
            List<DocumentCreditTransaction> entries,
            DocumentCreditTransactionType type,
            String operationId) {
        return entries.stream().filter(entry -> entry.getTransactionType() == type
                && operationId.equals(entry.getOperationId())).count();
    }

    public record Result(
            UUID walletId,
            int walletBalance,
            long reconstructedBalance,
            int entryCount,
            boolean chainValid,
            boolean reservationsValid) {
        public boolean reconciled() {
            return walletBalance == reconstructedBalance && chainValid && reservationsValid;
        }
    }
}
