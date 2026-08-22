package com.jobseekercopilot.paymentservice.service;

import com.jobseekercopilot.paymentservice.entity.AiTokenTransaction;
import com.jobseekercopilot.paymentservice.entity.AiTokenWallet;
import com.jobseekercopilot.paymentservice.exception.BadRequestException;
import com.jobseekercopilot.paymentservice.repository.AiTokenTransactionRepository;
import com.jobseekercopilot.paymentservice.repository.AiTokenWalletRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LedgerReconciliationService {
    private final AiTokenWalletRepository walletRepository;
    private final AiTokenTransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public LedgerReconciliationResult reconcile(UUID walletId) {
        AiTokenWallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new BadRequestException("Wallet was not found"));
        List<AiTokenTransaction> entries =
                transactionRepository.findByWalletIdOrderBySequenceNumberAsc(walletId);

        long expectedBefore = 0;
        boolean chainValid = true;
        for (AiTokenTransaction entry : entries) {
            if (entry.getBalanceBefore() != expectedBefore
                    || entry.getBalanceAfter()
                            != entry.getBalanceBefore() + entry.getBalanceDeltaTokens()) {
                chainValid = false;
            }
            expectedBefore = entry.getBalanceAfter();
        }

        long reconstructed =
                transactionRepository.sumBalanceDeltaTokensByWalletId(walletId);
        return new LedgerReconciliationResult(
                walletId,
                wallet.getBalanceTokens(),
                reconstructed,
                entries.size(),
                chainValid);
    }

    @Transactional(readOnly = true)
    public List<LedgerReconciliationResult> reconcileAll() {
        return walletRepository.findAll().stream()
                .map(wallet -> reconcile(wallet.getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public int verifyAllOrThrow() {
        List<LedgerReconciliationResult> results = reconcileAll();
        List<UUID> divergentWallets = results.stream()
                .filter(result -> !result.reconciled())
                .map(LedgerReconciliationResult::walletId)
                .toList();
        if (!divergentWallets.isEmpty()) {
            throw new IllegalStateException(
                    "AI Credit ledger reconciliation failed for "
                            + divergentWallets.size()
                            + " wallet(s)");
        }
        return results.size();
    }
}
