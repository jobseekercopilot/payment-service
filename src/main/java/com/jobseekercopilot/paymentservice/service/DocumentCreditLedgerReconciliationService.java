package com.jobseekercopilot.paymentservice.service;

import com.jobseekercopilot.paymentservice.entity.DocumentCreditReservation;
import com.jobseekercopilot.paymentservice.entity.DocumentCreditReservationStatus;
import com.jobseekercopilot.paymentservice.entity.DocumentCreditTransaction;
import com.jobseekercopilot.paymentservice.entity.DocumentCreditTransactionType;
import com.jobseekercopilot.paymentservice.entity.DocumentCreditWallet;
import com.jobseekercopilot.paymentservice.entity.DocumentGenerationDelivery;
import com.jobseekercopilot.paymentservice.repository.DocumentCreditReservationRepository;
import com.jobseekercopilot.paymentservice.repository.DocumentCreditTransactionRepository;
import com.jobseekercopilot.paymentservice.repository.DocumentCreditWalletRepository;
import com.jobseekercopilot.paymentservice.repository.DocumentGenerationDeliveryRepository;
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
    private final DocumentGenerationDeliveryRepository deliveryRepository;

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
        List<DocumentCreditReservation> reservations = reservationRepository.findByWalletId(walletId);
        boolean reservationsValid = reservations.stream()
                .allMatch(reservation -> reservationEvidenceValid(reservation, entries));
        long recordedDeliveries = reservations.stream()
                .mapToLong(reservation -> deliveryRepository
                        .findByReservationIdOrderByDocumentType(reservation.getId()).size())
                .sum();
        long deliverySpendEntries = entries.stream()
                .filter(entry -> entry.getTransactionType()
                        == DocumentCreditTransactionType.DOCUMENT_SPENT)
                .filter(entry -> entry.getOperationId()
                        .startsWith("DOCUMENT_DELIVERY_COMMIT:"))
                .count();
        reservationsValid = reservationsValid && recordedDeliveries == deliverySpendEntries;
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
        List<DocumentGenerationDelivery> deliveries =
                deliveryRepository.findByReservationIdOrderByDocumentType(
                        reservation.getId());
        long commits = deliveries.stream()
                .filter(delivery -> count(
                        entries,
                        DocumentCreditTransactionType.DOCUMENT_SPENT,
                        "DOCUMENT_DELIVERY_COMMIT:"
                                + delivery.getGeneratedDocumentId()) == 1)
                .count();
        long releases = count(entries, DocumentCreditTransactionType.DOCUMENT_RESERVATION_RELEASED,
                "DOCUMENT_RESERVATION_RELEASE:" + reservation.getId());
        if (creates != 1 || releases > 1) return false;
        return switch (reservation.getStatus()) {
            case RESERVED ->
                    deliveries.isEmpty() && commits == 0 && releases == 0;
            case COMMITTED ->
                    deliveries.size() == reservation.getDocumentCredits()
                            && commits == deliveries.size()
                            && releases == 0;
            case RELEASED ->
                    deliveries.isEmpty() && commits == 0 && releases == 1;
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
