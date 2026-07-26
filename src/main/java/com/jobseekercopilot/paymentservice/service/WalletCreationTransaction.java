package com.jobseekercopilot.paymentservice.service;

import com.jobseekercopilot.paymentservice.config.PaymentProperties;
import com.jobseekercopilot.paymentservice.entity.AiTokenTransaction;
import com.jobseekercopilot.paymentservice.entity.AiTokenWallet;
import com.jobseekercopilot.paymentservice.entity.TransactionType;
import com.jobseekercopilot.paymentservice.repository.AiTokenTransactionRepository;
import com.jobseekercopilot.paymentservice.repository.AiTokenWalletRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalletCreationTransaction {
    private static final Logger log = LoggerFactory.getLogger(WalletCreationTransaction.class);

    private final AiTokenWalletRepository walletRepository;
    private final AiTokenTransactionRepository transactionRepository;
    private final PaymentProperties paymentProperties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID findOrCreate(String userId) {
        return walletRepository.findByUserId(userId)
                .map(AiTokenWallet::getId)
                .orElseGet(() -> createWalletWithStarterTokens(userId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<UUID> findWalletId(String userId) {
        return walletRepository.findByUserId(userId).map(AiTokenWallet::getId);
    }

    private UUID createWalletWithStarterTokens(String userId) {
        long freeTokens = paymentProperties.getFreeStarterTokens();
        if (freeTokens < 0) {
            throw new IllegalStateException("Free starter AI Credit must not be negative");
        }
        AiTokenWallet wallet = AiTokenWallet.builder()
                .userId(userId)
                .balanceTokens(freeTokens)
                .lifetimePurchasedTokens(0)
                .lifetimeSpentTokens(0)
                .lifetimeRefundedTokens(0)
                .freeTrialGranted(true)
                .build();
        AiTokenWallet saved = walletRepository.saveAndFlush(wallet);
        AiTokenTransaction transaction = transactionRepository.save(AiTokenTransaction.builder()
                .userId(saved.getUserId())
                .walletId(saved.getId())
                .transactionType(TransactionType.FREE_TRIAL_GRANTED)
                .tokenAmount(freeTokens)
                .balanceDeltaTokens(freeTokens)
                .balanceBefore(0)
                .balanceAfter(freeTokens)
                .operationId("FREE_TRIAL:" + saved.getId())
                .description("Free starter AI Credit granted")
                .build());
        log.info("AI token wallet created userId={} walletId={} freeTokensGranted={} transactionId={}",
                userId,
                saved.getId(),
                freeTokens,
                transaction.getId());
        return saved.getId();
    }
}
