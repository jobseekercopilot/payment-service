package com.jobseekercopilot.paymentservice.service;

import com.jobseekercopilot.paymentservice.config.PaymentProperties;
import com.jobseekercopilot.paymentservice.entity.AiTokenWallet;
import com.jobseekercopilot.paymentservice.entity.DocumentCreditTransaction;
import com.jobseekercopilot.paymentservice.entity.DocumentCreditTransactionType;
import com.jobseekercopilot.paymentservice.entity.DocumentCreditWallet;
import com.jobseekercopilot.paymentservice.entity.DocumentCreditWalletStatus;
import com.jobseekercopilot.paymentservice.repository.AiTokenWalletRepository;
import com.jobseekercopilot.paymentservice.repository.DocumentCreditTransactionRepository;
import com.jobseekercopilot.paymentservice.repository.DocumentCreditWalletRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentCreditWalletCreationTransaction {
    private static final long LEGACY_UNITS_PER_MIGRATED_CREDIT = 10_000L;

    private final DocumentCreditWalletRepository walletRepository;
    private final DocumentCreditTransactionRepository transactionRepository;
    private final AiTokenWalletRepository legacyWalletRepository;
    private final PaymentProperties properties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID findOrCreate(String owner) {
        return walletRepository.findByUserId(owner)
                .map(DocumentCreditWallet::getId)
                .orElseGet(() -> create(owner));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<UUID> findWalletId(String owner) {
        return walletRepository.findByUserId(owner).map(DocumentCreditWallet::getId);
    }

    private UUID create(String owner) {
        AiTokenWallet legacy = legacyWalletRepository.findByUserId(owner).orElse(null);
        int openingBalance = legacy == null
                ? properties.getFreeDocumentCredits()
                : migrate(legacy.getBalanceTokens());
        boolean migrated = legacy != null;
        DocumentCreditWallet wallet = walletRepository.saveAndFlush(DocumentCreditWallet.builder()
                .userId(owner)
                .balanceCredits(openingBalance)
                .lifetimePurchasedCredits(legacy == null ? 0 : migrate(legacy.getLifetimePurchasedTokens()))
                .lifetimeSpentCredits(legacy == null ? 0 : migrate(legacy.getLifetimeSpentTokens()))
                .lifetimeReversedCredits(legacy == null ? 0 : migrate(legacy.getLifetimeRefundedTokens()))
                .freeAllowanceGranted(true)
                .migratedLegacyBalanceTokens(legacy == null ? null : legacy.getBalanceTokens())
                .lifecycleStatus(DocumentCreditWalletStatus.ACTIVE)
                .reviewDebtCredits(0)
                .build());
        transactionRepository.save(DocumentCreditTransaction.builder()
                .userId(owner)
                .walletId(wallet.getId())
                .transactionType(migrated
                        ? DocumentCreditTransactionType.LEGACY_VALUE_MIGRATED
                        : DocumentCreditTransactionType.FREE_ALLOWANCE_GRANTED)
                .documentCredits(openingBalance)
                .balanceDeltaCredits(openingBalance)
                .balanceBefore(0)
                .balanceAfter(openingBalance)
                .operationId((migrated ? "LEGACY_VALUE_MIGRATION:" : "FREE_ALLOWANCE:") + wallet.getId())
                .description(migrated
                        ? "Existing value migrated to document credits"
                        : "Free document credits granted")
                .referenceType(migrated ? "LEGACY_AI_TOKEN_WALLET" : null)
                .referenceId(migrated ? legacy.getId().toString() : null)
                .build());
        return wallet.getId();
    }

    private int migrate(long legacyUnits) {
        if (legacyUnits < 0) {
            throw new IllegalStateException("Legacy balance cannot be negative");
        }
        long credits = legacyUnits == 0
                ? 0
                : Math.addExact(legacyUnits, LEGACY_UNITS_PER_MIGRATED_CREDIT - 1)
                        / LEGACY_UNITS_PER_MIGRATED_CREDIT;
        return Math.toIntExact(credits);
    }
}
