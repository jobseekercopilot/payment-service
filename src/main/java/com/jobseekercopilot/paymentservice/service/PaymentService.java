package com.jobseekercopilot.paymentservice.service;

import com.jobseekercopilot.paymentservice.config.PaymentProperties;
import com.jobseekercopilot.paymentservice.dto.CommitReservationRequest;
import com.jobseekercopilot.paymentservice.dto.CommitReservationResponse;
import com.jobseekercopilot.paymentservice.dto.ConfirmStripePurchaseRequest;
import com.jobseekercopilot.paymentservice.dto.CreateReservationRequest;
import com.jobseekercopilot.paymentservice.dto.DemoPurchaseResponse;
import com.jobseekercopilot.paymentservice.dto.EstimateRequest;
import com.jobseekercopilot.paymentservice.dto.EstimateResponse;
import com.jobseekercopilot.paymentservice.dto.PricingPlansResponse;
import com.jobseekercopilot.paymentservice.dto.ReleaseReservationResponse;
import com.jobseekercopilot.paymentservice.dto.ReservationResponse;
import com.jobseekercopilot.paymentservice.dto.TokenPricingPlanResponse;
import com.jobseekercopilot.paymentservice.dto.TransactionResponse;
import com.jobseekercopilot.paymentservice.dto.TransactionsResponse;
import com.jobseekercopilot.paymentservice.dto.WalletSummaryResponse;
import com.jobseekercopilot.paymentservice.entity.AiTokenReservation;
import com.jobseekercopilot.paymentservice.entity.AiTokenTransaction;
import com.jobseekercopilot.paymentservice.entity.AiTokenWallet;
import com.jobseekercopilot.paymentservice.entity.ReservationStatus;
import com.jobseekercopilot.paymentservice.entity.TransactionType;
import com.jobseekercopilot.paymentservice.exception.BadRequestException;
import com.jobseekercopilot.paymentservice.exception.InsufficientTokensException;
import com.jobseekercopilot.paymentservice.repository.AiTokenReservationRepository;
import com.jobseekercopilot.paymentservice.repository.AiTokenTransactionRepository;
import com.jobseekercopilot.paymentservice.repository.AiTokenWalletRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final String STRIPE_CHECKOUT_SESSION = "STRIPE_CHECKOUT_SESSION";

    private final AiTokenWalletRepository walletRepository;
    private final AiTokenTransactionRepository transactionRepository;
    private final AiTokenReservationRepository reservationRepository;
    private final PaymentProperties paymentProperties;
    private final WalletProvisioner walletProvisioner;
    private final LedgerTransactionExecutor ledgerTransactionExecutor;

    public WalletSummaryResponse wallet(String userId) {
        walletProvisioner.ensureWallet(userId);
        return ledgerTransactionExecutor.read(() -> mapWallet(loadWallet(userId)));
    }

    @Transactional(readOnly = true)
    public TransactionsResponse transactions(String userId, int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        return TransactionsResponse.builder()
                .userId(userId)
                .transactions(transactionRepository.findByUserIdOrderBySequenceNumberDesc(
                                userId, PageRequest.of(0, boundedLimit))
                        .stream()
                        .map(this::mapTransaction)
                        .toList())
                .build();
    }

    public PricingPlansResponse pricing() {
        return PricingPlansResponse.builder()
                .plans(paymentProperties.activePricingPlans().stream()
                        .map(this::mapPlan)
                        .toList())
                .build();
    }

    public DemoPurchaseResponse demoPurchase(String userId, String pricingPlanId) {
        long startedAt = System.nanoTime();
        PaymentProperties.PricingPlan plan = paymentProperties.activePricingPlan(pricingPlanId)
                .orElseThrow(() -> new BadRequestException("Unknown or inactive pricing plan: " + pricingPlanId));
        walletProvisioner.ensureWallet(userId);
        return ledgerTransactionExecutor.execute(() -> {
            AiTokenWallet wallet = loadWalletForUpdate(userId);
            long before = wallet.getBalanceTokens();
            long after = checkedAdd(before, plan.getTokenAmount());
            wallet.setBalanceTokens(after);
            wallet.setLifetimePurchasedTokens(
                    checkedAdd(wallet.getLifetimePurchasedTokens(), plan.getTokenAmount()));
            AiTokenWallet savedWallet = walletRepository.save(wallet);
            AiTokenTransaction transaction = transactionRepository.save(transaction(
                    savedWallet,
                    TransactionType.DEMO_PURCHASE,
                    plan.getTokenAmount(),
                    before,
                    after,
                    "DEMO_PURCHASE:" + UUID.randomUUID(),
                    "Demo AI Credit purchase: " + plan.getName(),
                    "PRICING_PLAN",
                    plan.getId()));
            log.info("Demo token purchase recorded userId={} pricingPlanId={} transactionId={} tokens={} balanceBefore={} balanceAfter={} durationMs={}",
                    userId,
                    pricingPlanId,
                    transaction.getId(),
                    plan.getTokenAmount(),
                    before,
                    after,
                    (System.nanoTime() - startedAt) / 1_000_000);
            return DemoPurchaseResponse.builder()
                    .wallet(mapWallet(savedWallet))
                    .transaction(mapTransaction(transaction))
                    .build();
        });
    }

    public DemoPurchaseResponse confirmStripePurchase(
            String authenticatedOwner,
            ConfirmStripePurchaseRequest request) {
        long startedAt = System.nanoTime();
        if (!authenticatedOwner.equals(request.getUserId())) {
            throw new BadRequestException("Payment owner does not match authenticated context");
        }
        PaymentProperties.PricingPlan plan = paymentProperties.activePricingPlan(request.getPricingPlanId())
                .orElseThrow(() -> new BadRequestException(
                        "Unknown or inactive pricing plan: " + request.getPricingPlanId()));
        if (plan.getTokenAmount() != request.getTokenAmount()) {
            throw new BadRequestException("Stripe purchase token amount does not match pricing plan");
        }

        walletProvisioner.ensureWallet(request.getUserId());
        return ledgerTransactionExecutor.execute(() -> {
            AiTokenWallet wallet = loadWalletForUpdate(request.getUserId());
            return transactionRepository.findFirstByTransactionTypeAndReferenceTypeAndReferenceId(
                            TransactionType.PURCHASE,
                            STRIPE_CHECKOUT_SESSION,
                            request.getStripeSessionId())
                    .map(existingTransaction -> {
                        log.info("Duplicate Stripe purchase ignored userId={} stripeSessionId={} transactionId={} durationMs={}",
                                request.getUserId(),
                                request.getStripeSessionId(),
                                existingTransaction.getId(),
                                (System.nanoTime() - startedAt) / 1_000_000);
                        return DemoPurchaseResponse.builder()
                                .wallet(mapWallet(walletRepository
                                        .findById(existingTransaction.getWalletId())
                                        .orElse(wallet)))
                                .transaction(mapTransaction(existingTransaction))
                                .build();
                    })
                    .orElseGet(() -> recordStripePurchase(wallet, plan, request));
        });
    }

    public EstimateResponse estimate(String userId, EstimateRequest request) {
        long total = checkedAdd(
                request.getEstimatedInputTokens(), request.getEstimatedOutputTokens());
        walletProvisioner.ensureWallet(userId);
        return ledgerTransactionExecutor.read(() -> {
            AiTokenWallet wallet = loadWallet(userId);
            return EstimateResponse.builder()
                    .feature(request.getFeature())
                    .estimatedTotalTokens(total)
                    .estimatedCostTokens(total)
                    .userBalanceTokens(wallet.getBalanceTokens())
                    .canAfford(wallet.getBalanceTokens() >= total)
                    .build();
        });
    }

    public ReservationResponse createReservation(String userId, CreateReservationRequest request) {
        long startedAt = System.nanoTime();
        if (request.getEstimatedTokens() <= 0) {
            throw new BadRequestException("Reservation token estimate must be positive");
        }
        walletProvisioner.ensureWallet(userId);
        return ledgerTransactionExecutor.execute(() -> {
            AiTokenWallet wallet = loadWalletForUpdate(userId);
            if (wallet.getBalanceTokens() < request.getEstimatedTokens()) {
                log.warn("Insufficient AI token balance userId={} feature={} requestedTokens={} balanceTokens={}",
                        userId,
                        request.getFeature(),
                        request.getEstimatedTokens(),
                        wallet.getBalanceTokens());
                throw new InsufficientTokensException();
            }

            long before = wallet.getBalanceTokens();
            long after = checkedSubtract(before, request.getEstimatedTokens());
            wallet.setBalanceTokens(after);
            AiTokenWallet savedWallet = walletRepository.save(wallet);

            AiTokenReservation reservation = reservationRepository.save(AiTokenReservation.builder()
                    .userId(userId)
                    .walletId(savedWallet.getId())
                    .feature(request.getFeature())
                    .reservedTokens(request.getEstimatedTokens())
                    .status(ReservationStatus.RESERVED)
                    .referenceType(request.getReferenceType())
                    .referenceId(request.getReferenceId())
                    .build());
            AiTokenTransaction transaction = transactionRepository.save(transaction(
                    savedWallet,
                    TransactionType.RESERVATION,
                    request.getEstimatedTokens(),
                    before,
                    after,
                    "RESERVATION:" + reservation.getId(),
                    "AI Credit reservation for " + request.getFeature(),
                    request.getReferenceType(),
                    request.getReferenceId()));
            log.info("AI token reservation created userId={} reservationId={} transactionId={} feature={} reservedTokens={} balanceBefore={} balanceAfter={} durationMs={}",
                    userId,
                    reservation.getId(),
                    transaction.getId(),
                    request.getFeature(),
                    request.getEstimatedTokens(),
                    before,
                    after,
                    (System.nanoTime() - startedAt) / 1_000_000);

            return ReservationResponse.builder()
                    .reservationId(reservation.getId())
                    .userId(userId)
                    .reservedTokens(reservation.getReservedTokens())
                    .balanceAfterReservation(after)
                    .status(reservation.getStatus())
                    .build();
        });
    }

    @Transactional
    public CommitReservationResponse commitReservation(
            String userId,
            UUID reservationId,
            CommitReservationRequest request) {
        if (request.getActualTokens() == null || request.getActualTokens() < 0) {
            throw new BadRequestException("Actual token usage must not be negative");
        }
        long actualTokens = request.getActualTokens();
        AiTokenReservation reservation = loadReservationForUpdate(userId, reservationId);
        if (reservation.getStatus() == ReservationStatus.COMMITTED) {
            return replayCommittedReservation(reservation, actualTokens);
        }
        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            throw new BadRequestException(
                    "Reservation cannot be committed from " + reservation.getStatus() + " status");
        }
        long extraTokens = actualTokens > reservation.getReservedTokens()
                ? checkedSubtract(actualTokens, reservation.getReservedTokens())
                : 0;
        long releasedTokens = actualTokens < reservation.getReservedTokens()
                ? checkedSubtract(reservation.getReservedTokens(), actualTokens)
                : 0;
        AiTokenWallet wallet = loadWalletForUpdate(reservation.getWalletId());
        long balanceBeforeCommit = wallet.getBalanceTokens();
        if (balanceBeforeCommit < extraTokens) {
            log.warn("Reservation commit exceeded balance userId={} reservationId={} actualTokens={} reservedTokens={} balanceTokens={}",
                    userId,
                    reservationId,
                    actualTokens,
                    reservation.getReservedTokens(),
                    balanceBeforeCommit);
            throw new BadRequestException("Actual token usage exceeded reservation and available balance");
        }

        long balanceAfterSpend = checkedSubtract(balanceBeforeCommit, extraTokens);
        long balanceAfterRelease = checkedAdd(balanceAfterSpend, releasedTokens);
        wallet.setBalanceTokens(balanceAfterRelease);
        wallet.setLifetimeSpentTokens(
                checkedAdd(wallet.getLifetimeSpentTokens(), actualTokens));
        AiTokenWallet savedWallet = walletRepository.save(wallet);

        String operationId = "RESERVATION_COMMIT:" + reservationId;
        AiTokenTransaction spendTransaction = transactionRepository.save(transaction(
                savedWallet,
                TransactionType.SPEND,
                actualTokens,
                balanceBeforeCommit,
                balanceAfterSpend,
                operationId,
                descriptionOrDefault(request.getDescription()),
                reservation.getReferenceType(),
                reservation.getReferenceId()));
        if (releasedTokens > 0) {
            transactionRepository.save(transaction(
                    savedWallet,
                    TransactionType.RESERVATION_RELEASED,
                    releasedTokens,
                    balanceAfterSpend,
                    balanceAfterRelease,
                    operationId,
                    "Released unused reserved AI Credit",
                    reservation.getReferenceType(),
                    reservation.getReferenceId()));
        }

        reservation.setCommittedTokens(actualTokens);
        reservation.setReleasedTokens(releasedTokens);
        reservation.setStatus(ReservationStatus.COMMITTED);
        reservation.setCommittedAt(Instant.now());
        reservationRepository.save(reservation);
        log.info("AI token reservation committed userId={} reservationId={} spendTransactionId={} actualTokens={} releasedTokens={} balanceAfter={}",
                userId,
                reservationId,
                spendTransaction.getId(),
                actualTokens,
                releasedTokens,
                savedWallet.getBalanceTokens());

        return CommitReservationResponse.builder()
                .reservationId(reservation.getId())
                .committedTokens(actualTokens)
                .releasedTokens(releasedTokens)
                .wallet(mapWallet(savedWallet))
                .spendTransaction(mapTransaction(spendTransaction))
                .build();
    }

    @Transactional
    public ReleaseReservationResponse releaseReservation(String userId, UUID reservationId, String reason) {
        long startedAt = System.nanoTime();
        AiTokenReservation reservation = loadReservationForUpdate(userId, reservationId);
        if (reservation.getStatus() == ReservationStatus.RELEASED) {
            return replayReleasedReservation(reservation);
        }
        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            throw new BadRequestException(
                    "Reservation cannot be released from " + reservation.getStatus() + " status");
        }
        AiTokenWallet wallet = loadWalletForUpdate(reservation.getWalletId());
        long before = wallet.getBalanceTokens();
        long releasedTokens = reservation.getReservedTokens();
        wallet.setBalanceTokens(checkedAdd(before, releasedTokens));
        AiTokenWallet savedWallet = walletRepository.save(wallet);
        AiTokenTransaction transaction = transactionRepository.save(transaction(
                savedWallet,
                TransactionType.RESERVATION_RELEASED,
                releasedTokens,
                before,
                savedWallet.getBalanceTokens(),
                "RESERVATION_RELEASE:" + reservationId,
                reason == null || reason.isBlank() ? "Released AI Credit reservation" : reason,
                reservation.getReferenceType(),
                reservation.getReferenceId()));

        reservation.setReleasedTokens(releasedTokens);
        reservation.setStatus(ReservationStatus.RELEASED);
        reservation.setReleasedAt(Instant.now());
        reservationRepository.save(reservation);
        log.info("AI token reservation released userId={} reservationId={} transactionId={} releasedTokens={} balanceBefore={} balanceAfter={} reason={} durationMs={}",
                userId,
                reservationId,
                transaction.getId(),
                releasedTokens,
                before,
                savedWallet.getBalanceTokens(),
                reason,
                (System.nanoTime() - startedAt) / 1_000_000);

        return ReleaseReservationResponse.builder()
                .reservationId(reservation.getId())
                .releasedTokens(releasedTokens)
                .wallet(mapWallet(savedWallet))
                .build();
    }

    private AiTokenWallet loadWallet(String userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("Provisioned AI Credit wallet was not found"));
    }

    private AiTokenWallet loadWalletForUpdate(String userId) {
        return walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException("Provisioned AI Credit wallet was not found"));
    }

    private AiTokenWallet loadWalletForUpdate(UUID walletId) {
        return walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new BadRequestException("Reservation wallet was not found"));
    }

    private AiTokenReservation loadReservationForUpdate(String userId, UUID reservationId) {
        return reservationRepository.findByIdAndUserIdForUpdate(reservationId, userId)
                .orElseThrow(() -> new BadRequestException("Reservation was not found"));
    }

    private DemoPurchaseResponse recordStripePurchase(
            AiTokenWallet wallet,
            PaymentProperties.PricingPlan plan,
            ConfirmStripePurchaseRequest request) {
        long before = wallet.getBalanceTokens();
        long after = checkedAdd(before, plan.getTokenAmount());
        wallet.setBalanceTokens(after);
        wallet.setLifetimePurchasedTokens(
                checkedAdd(wallet.getLifetimePurchasedTokens(), plan.getTokenAmount()));
        AiTokenWallet savedWallet = walletRepository.save(wallet);
        AiTokenTransaction transaction = transactionRepository.save(transaction(
                savedWallet,
                TransactionType.PURCHASE,
                plan.getTokenAmount(),
                before,
                after,
                STRIPE_CHECKOUT_SESSION + ":" + request.getStripeSessionId(),
                "Stripe AI Credit purchase: " + plan.getName(),
                STRIPE_CHECKOUT_SESSION,
                request.getStripeSessionId()));
        log.info("Stripe token purchase confirmed userId={} pricingPlanId={} stripeSessionId={} transactionId={} tokens={} balanceBefore={} balanceAfter={}",
                request.getUserId(),
                request.getPricingPlanId(),
                request.getStripeSessionId(),
                transaction.getId(),
                plan.getTokenAmount(),
                before,
                after);
        return DemoPurchaseResponse.builder()
                .wallet(mapWallet(savedWallet))
                .transaction(mapTransaction(transaction))
                .build();
    }

    private String descriptionOrDefault(String description) {
        return description == null || description.isBlank()
                ? "AI Credit spend committed from reservation"
                : description;
    }

    private AiTokenTransaction transaction(
            AiTokenWallet wallet,
            TransactionType type,
            long amount,
            long before,
            long after,
            String operationId,
            String description,
            String referenceType,
            String referenceId) {
        if (amount < 0
                || before < 0
                || after < 0
                || operationId == null
                || operationId.isBlank()
                || operationId.length() > 255) {
            throw new IllegalArgumentException("Invalid AI Credit ledger balance transition");
        }
        final long delta;
        try {
            delta = Math.subtractExact(after, before);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Invalid AI Credit ledger balance transition", exception);
        }
        return AiTokenTransaction.builder()
                .userId(wallet.getUserId())
                .walletId(wallet.getId())
                .transactionType(type)
                .tokenAmount(amount)
                .balanceDeltaTokens(delta)
                .balanceBefore(before)
                .balanceAfter(after)
                .operationId(operationId)
                .description(description)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .build();
    }

    private long checkedAdd(long left, long right) {
        if (left < 0 || right < 0) {
            throw new BadRequestException("AI Credit amount must not be negative");
        }
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw new BadRequestException("AI Credit amount exceeds the supported range");
        }
    }

    private long checkedSubtract(long left, long right) {
        if (left < 0 || right < 0) {
            throw new BadRequestException("AI Credit amount must not be negative");
        }
        try {
            long result = Math.subtractExact(left, right);
            if (result < 0) {
                throw new BadRequestException("AI Credit amount must not produce a negative balance");
            }
            return result;
        } catch (ArithmeticException exception) {
            throw new BadRequestException("AI Credit amount exceeds the supported range");
        }
    }

    private CommitReservationResponse replayCommittedReservation(
            AiTokenReservation reservation,
            long requestedActualTokens) {
        if (!Objects.equals(reservation.getCommittedTokens(), requestedActualTokens)) {
            throw new BadRequestException(
                    "Reservation was already committed with a different token amount");
        }
        AiTokenWallet wallet = loadWalletForUpdate(reservation.getWalletId());
        AiTokenTransaction spendTransaction = transactionRepository
                .findByWalletIdAndOperationIdAndTransactionType(
                        wallet.getId(),
                        "RESERVATION_COMMIT:" + reservation.getId(),
                        TransactionType.SPEND)
                .orElseThrow(() -> new IllegalStateException(
                        "Committed reservation is missing its spend ledger entry"));
        log.info("Duplicate reservation commit returned without mutation userId={} reservationId={} transactionId={}",
                reservation.getUserId(),
                reservation.getId(),
                spendTransaction.getId());
        return CommitReservationResponse.builder()
                .reservationId(reservation.getId())
                .committedTokens(reservation.getCommittedTokens())
                .releasedTokens(reservation.getReleasedTokens() == null
                        ? 0
                        : reservation.getReleasedTokens())
                .wallet(mapWallet(wallet))
                .spendTransaction(mapTransaction(spendTransaction))
                .build();
    }

    private ReleaseReservationResponse replayReleasedReservation(AiTokenReservation reservation) {
        AiTokenWallet wallet = loadWalletForUpdate(reservation.getWalletId());
        if (reservation.getReleasedTokens() == null) {
            throw new IllegalStateException(
                    "Released reservation is missing its released token amount");
        }
        transactionRepository.findByWalletIdAndOperationIdAndTransactionType(
                        wallet.getId(),
                        "RESERVATION_RELEASE:" + reservation.getId(),
                        TransactionType.RESERVATION_RELEASED)
                .orElseThrow(() -> new IllegalStateException(
                        "Released reservation is missing its release ledger entry"));
        long releasedTokens = reservation.getReleasedTokens();
        log.info("Duplicate reservation release returned without mutation userId={} reservationId={}",
                reservation.getUserId(),
                reservation.getId());
        return ReleaseReservationResponse.builder()
                .reservationId(reservation.getId())
                .releasedTokens(releasedTokens)
                .wallet(mapWallet(wallet))
                .build();
    }

    private WalletSummaryResponse mapWallet(AiTokenWallet wallet) {
        return WalletSummaryResponse.builder()
                .userId(wallet.getUserId())
                .balanceTokens(wallet.getBalanceTokens())
                .lifetimePurchasedTokens(wallet.getLifetimePurchasedTokens())
                .lifetimeSpentTokens(wallet.getLifetimeSpentTokens())
                .lifetimeRefundedTokens(wallet.getLifetimeRefundedTokens())
                .freeTrialGranted(wallet.isFreeTrialGranted())
                .build();
    }

    private TransactionResponse mapTransaction(AiTokenTransaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .transactionType(transaction.getTransactionType())
                .tokenAmount(transaction.getTokenAmount())
                .balanceDeltaTokens(transaction.getBalanceDeltaTokens())
                .balanceBefore(transaction.getBalanceBefore())
                .balanceAfter(transaction.getBalanceAfter())
                .operationId(transaction.getOperationId())
                .description(transaction.getDescription())
                .referenceType(transaction.getReferenceType())
                .referenceId(transaction.getReferenceId())
                .createdAt(transaction.getCreatedAt())
                .build();
    }

    private TokenPricingPlanResponse mapPlan(PaymentProperties.PricingPlan plan) {
        return TokenPricingPlanResponse.builder()
                .id(plan.getId())
                .name(plan.getName())
                .description(plan.getDescription())
                .tokenAmount(plan.getTokenAmount())
                .priceGbpPence(plan.getPriceGbpPence())
                .build();
    }
}
