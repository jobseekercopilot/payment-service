package com.jobseekercopilot.paymentservice.service;

import com.jobseekercopilot.paymentservice.config.PaymentProperties;
import com.jobseekercopilot.paymentservice.dto.AccountPaymentLifecycleResponse;
import com.jobseekercopilot.paymentservice.dto.CreateDocumentCreditReservationRequest;
import com.jobseekercopilot.paymentservice.dto.DocumentCreditCatalogResponse;
import com.jobseekercopilot.paymentservice.dto.DocumentCreditCommitResponse;
import com.jobseekercopilot.paymentservice.dto.DocumentCreditPlanResponse;
import com.jobseekercopilot.paymentservice.dto.DocumentCreditReleaseResponse;
import com.jobseekercopilot.paymentservice.dto.DocumentCreditReservationResponse;
import com.jobseekercopilot.paymentservice.dto.DocumentCreditTransactionResponse;
import com.jobseekercopilot.paymentservice.dto.DocumentCreditTransactionsResponse;
import com.jobseekercopilot.paymentservice.dto.DocumentCreditWalletResponse;
import com.jobseekercopilot.paymentservice.dto.PromotionResponse;
import com.jobseekercopilot.paymentservice.entity.DocumentCreditReservation;
import com.jobseekercopilot.paymentservice.entity.DocumentCreditReservationStatus;
import com.jobseekercopilot.paymentservice.entity.DocumentCreditTransaction;
import com.jobseekercopilot.paymentservice.entity.DocumentCreditTransactionType;
import com.jobseekercopilot.paymentservice.entity.DocumentCreditWallet;
import com.jobseekercopilot.paymentservice.entity.DocumentCreditWalletStatus;
import com.jobseekercopilot.paymentservice.entity.FoundingPromotionCampaign;
import com.jobseekercopilot.paymentservice.exception.PaymentApiException;
import com.jobseekercopilot.paymentservice.repository.DocumentCreditReservationRepository;
import com.jobseekercopilot.paymentservice.repository.DocumentCreditTransactionRepository;
import com.jobseekercopilot.paymentservice.repository.DocumentCreditWalletRepository;
import com.jobseekercopilot.paymentservice.repository.FoundingPromotionCampaignRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentCreditService {
    private final DocumentCreditWalletRepository walletRepository;
    private final DocumentCreditTransactionRepository transactionRepository;
    private final DocumentCreditReservationRepository reservationRepository;
    private final FoundingPromotionCampaignRepository campaignRepository;
    private final DocumentCreditWalletProvisioner provisioner;
    private final PaymentProperties properties;

    @Transactional(readOnly = true)
    public DocumentCreditCatalogResponse catalog() {
        boolean checkoutReady = checkoutReady();
        FoundingPromotionCampaign campaign = campaignRepository
                .findById(properties.getPromotion().getId()).orElse(null);
        boolean promotionEnabled = checkoutReady
                && properties.getPromotion().isEnabled()
                && properties.getPromotion().isReleaseAuthorised()
                && campaign != null
                && campaign.isEnabled();
        PromotionResponse.Status promotionStatus;
        if (!promotionEnabled) {
            promotionStatus = PromotionResponse.Status.DISABLED;
        } else if (campaign.getActiveReservations() + campaign.getCompletedClaims()
                >= campaign.getCustomerLimit()) {
            promotionStatus = PromotionResponse.Status.EXHAUSTED;
        } else {
            promotionStatus = PromotionResponse.Status.AVAILABLE;
        }
        return DocumentCreditCatalogResponse.builder()
                .catalogVersion(properties.getCatalogVersion())
                .currency(properties.getCurrency())
                .billingCountry(properties.getBillingCountry())
                .taxTreatment(properties.getTaxTreatment())
                .taxStatus(properties.getTaxStatus())
                .displayedPriceIsCheckoutTotal(true)
                .automaticRenewal(false)
                .creditUnit("DOCUMENT")
                .freeAllowanceCredits(properties.getFreeDocumentCredits())
                .plans(properties.activePricingPlans().stream()
                        .map(plan -> DocumentCreditPlanResponse.builder()
                                .id(plan.getId())
                                .name(plan.getName())
                                .description(plan.getDescription())
                                .documentCredits(plan.getDocumentCredits())
                                .priceMinor(plan.getPriceGbpPence())
                                .currency(properties.getCurrency())
                                .fullApplicationEquivalent(plan.getDocumentCredits() / 2)
                                .promotionBonusDocumentCredits(
                                        promotionEnabled ? promotionBonus(plan.getDocumentCredits()) : 0)
                                .active(plan.isActive())
                                .sortOrder(plan.getSortOrder())
                                .build())
                        .toList())
                .promotion(PromotionResponse.builder()
                        .id(properties.getPromotion().getId())
                        .enabled(promotionEnabled)
                        .status(promotionStatus)
                        .bonusPercent(properties.getPromotion().getBonusPercent())
                        .customerLimit(properties.getPromotion().getCustomerLimit())
                        .build())
                .build();
    }

    public boolean checkoutReady() {
        return properties.getCheckout().isEnabled()
                && properties.getCheckout().isReleaseAuthorised()
                && properties.getTaxStatus() != PaymentProperties.TaxStatus.NOT_CONFIGURED
                && properties.getLegalEntity().getType()
                != PaymentProperties.LegalEntityType.NOT_CONFIGURED
                && properties.getLegalEntity().isReviewed()
                && properties.getLegalEntity().getConfigurationVersion() != null
                && !properties.getLegalEntity().getConfigurationVersion().isBlank();
    }

    public int promotionBonus(int baseCredits) {
        return Math.toIntExact(Math.addExact(
                Math.multiplyExact((long) baseCredits, properties.getPromotion().getBonusPercent()),
                99L) / 100L);
    }

    public DocumentCreditWalletResponse wallet(String owner) {
        provisioner.ensureWallet(owner);
        return mapWallet(walletRepository.findByUserId(owner).orElseThrow());
    }

    @Transactional(readOnly = true)
    public DocumentCreditTransactionsResponse transactions(String owner, int limit) {
        int bounded = Math.max(1, Math.min(limit, 100));
        return DocumentCreditTransactionsResponse.builder()
                .transactions(transactionRepository.findByUserIdOrderBySequenceNumberDesc(
                                owner, PageRequest.of(0, bounded)).stream()
                        .map(this::mapTransaction)
                        .toList())
                .build();
    }

    @Transactional
    public DocumentCreditReservationResponse reserve(
            String owner, CreateDocumentCreditReservationRequest request) {
        provisioner.ensureWallet(owner);
        DocumentCreditWallet wallet = walletRepository.findByUserIdForUpdate(owner).orElseThrow();
        requireSpendable(wallet);
        DocumentCreditReservation existing = reservationRepository
                .findByUserIdAndOperationKey(owner, request.getOperationKey()).orElse(null);
        if (existing != null) {
            if (existing.getDocumentCredits() != request.getDocumentCredits()
                    || existing.isRegeneration() != request.isRegeneration()
                    || !Objects.equals(existing.getReferenceType(), request.getReferenceType())
                    || !Objects.equals(existing.getReferenceId(), request.getReferenceId())) {
                throw api(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT",
                        "The document-credit operation key was already used for a different request.");
            }
            return mapReservation(existing, creationBalance(existing));
        }
        if (wallet.getBalanceCredits() < request.getDocumentCredits()) {
            throw api(HttpStatus.PAYMENT_REQUIRED, "INSUFFICIENT_DOCUMENT_CREDITS",
                    "There are not enough document credits for this generation.");
        }
        int before = wallet.getBalanceCredits();
        int after = Math.subtractExact(before, request.getDocumentCredits());
        wallet.setBalanceCredits(after);
        walletRepository.save(wallet);
        DocumentCreditReservation reservation = reservationRepository.save(
                DocumentCreditReservation.builder()
                        .userId(owner)
                        .walletId(wallet.getId())
                        .operationKey(request.getOperationKey())
                        .documentCredits(request.getDocumentCredits())
                        .status(DocumentCreditReservationStatus.RESERVED)
                        .regeneration(request.isRegeneration())
                        .referenceType(request.getReferenceType())
                        .referenceId(request.getReferenceId())
                        .expiresAt(Instant.now().plus(properties.getReservationRecovery().getTtl()))
                        .lastTransitionReason("CREATED")
                        .build());
        transactionRepository.save(transaction(
                wallet, DocumentCreditTransactionType.DOCUMENT_RESERVED,
                request.getDocumentCredits(), before, after,
                "DOCUMENT_RESERVATION_CREATE:" + request.getOperationKey(),
                request.isRegeneration()
                        ? "Document regeneration credit reserved"
                        : "Document generation credit reserved",
                request.getReferenceType(), request.getReferenceId()));
        return mapReservation(reservation, after);
    }

    @Transactional(readOnly = true)
    public DocumentCreditReservationResponse reservation(String owner, UUID id) {
        DocumentCreditReservation reservation = reservationRepository.findByIdAndUserId(id, owner)
                .orElseThrow(() -> api(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND",
                        "Document-credit reservation was not found."));
        return mapReservation(reservation, creationBalance(reservation));
    }

    @Transactional
    public DocumentCreditCommitResponse commit(String owner, UUID id) {
        DocumentCreditReservation reservation = reservationRepository
                .findByIdAndUserIdForUpdate(id, owner)
                .orElseThrow(() -> api(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND",
                        "Document-credit reservation was not found."));
        DocumentCreditWallet wallet = walletRepository.findByIdForUpdate(reservation.getWalletId())
                .orElseThrow();
        if (reservation.getStatus() == DocumentCreditReservationStatus.COMMITTED) {
            return commitResponse(reservation, wallet);
        }
        if (reservation.getStatus() != DocumentCreditReservationStatus.RESERVED) {
            throw api(HttpStatus.CONFLICT, "RESERVATION_ALREADY_RELEASED",
                    "Released document credits cannot be charged.");
        }
        if (!reservation.getExpiresAt().isAfter(Instant.now())) {
            throw api(HttpStatus.CONFLICT, "RESERVATION_EXPIRED",
                    "The document-credit reservation expired before delivery.");
        }
        int balance = wallet.getBalanceCredits();
        transactionRepository.save(transaction(
                wallet, DocumentCreditTransactionType.DOCUMENT_SPENT,
                reservation.getDocumentCredits(), balance, balance,
                "DOCUMENT_RESERVATION_COMMIT:" + reservation.getId(),
                reservation.isRegeneration()
                        ? "Regenerated document delivered"
                        : deliveredDescription(reservation.getDocumentCredits()),
                reservation.getReferenceType(), reservation.getReferenceId()));
        wallet.setLifetimeSpentCredits(Math.addExact(
                wallet.getLifetimeSpentCredits(), reservation.getDocumentCredits()));
        walletRepository.save(wallet);
        reservation.setStatus(DocumentCreditReservationStatus.COMMITTED);
        reservation.setCommittedAt(Instant.now());
        reservation.setLastTransitionReason("SUCCESSFULLY_DELIVERED");
        reservationRepository.save(reservation);
        return commitResponse(reservation, wallet);
    }

    @Transactional
    public DocumentCreditReleaseResponse release(String owner, UUID id, String reason) {
        DocumentCreditReservation reservation = reservationRepository
                .findByIdAndUserIdForUpdate(id, owner)
                .orElseThrow(() -> api(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND",
                        "Document-credit reservation was not found."));
        DocumentCreditWallet wallet = walletRepository.findByIdForUpdate(reservation.getWalletId())
                .orElseThrow();
        if (reservation.getStatus() == DocumentCreditReservationStatus.RELEASED) {
            return releaseResponse(reservation, wallet);
        }
        if (reservation.getStatus() == DocumentCreditReservationStatus.COMMITTED) {
            throw api(HttpStatus.CONFLICT, "RESERVATION_ALREADY_COMMITTED",
                    "Delivered document credits cannot be released.");
        }
        return releaseLocked(reservation, wallet, safeReason(reason));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean reconcileExpiredReservation(UUID id, Instant reconciliationTime) {
        DocumentCreditReservation reservation = reservationRepository.findByIdForUpdate(id)
                .orElse(null);
        if (reservation == null
                || reservation.getStatus() != DocumentCreditReservationStatus.RESERVED
                || reservation.getExpiresAt().isAfter(reconciliationTime)) {
            return false;
        }
        DocumentCreditWallet wallet = walletRepository.findByIdForUpdate(reservation.getWalletId())
                .orElseThrow();
        releaseLocked(reservation, wallet, "Expired document-credit hold automatically released");
        return true;
    }

    @Transactional
    public void beginAccessRevocation(String owner) {
        provisioner.ensureWallet(owner);
        DocumentCreditWallet wallet = walletRepository.findByUserIdForUpdate(owner).orElseThrow();
        if (wallet.getLifecycleStatus() == DocumentCreditWalletStatus.REVOKED
                || wallet.getLifecycleStatus() == DocumentCreditWalletStatus.REVOCATION_PENDING) {
            return;
        }
        if (wallet.getLifecycleStatus() == DocumentCreditWalletStatus.BLOCKED_REVIEW
                || wallet.getReviewDebtCredits() > 0) {
            throw api(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_FINANCIAL_REVIEW_REQUIRED",
                    "Account deletion is waiting for payment review to be completed.");
        }
        wallet.setLifecycleStatus(DocumentCreditWalletStatus.REVOCATION_PENDING);
        walletRepository.save(wallet);
    }

    @Transactional
    public AccountPaymentLifecycleResponse finalizeAccessRevocation(String owner) {
        provisioner.ensureWallet(owner);
        DocumentCreditWallet wallet = walletRepository.findByUserIdForUpdate(owner).orElseThrow();
        if (wallet.getLifecycleStatus() == DocumentCreditWalletStatus.BLOCKED_REVIEW
                || wallet.getReviewDebtCredits() > 0) {
            throw api(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_FINANCIAL_REVIEW_REQUIRED",
                    "Account deletion is waiting for payment review to be completed.");
        }
        for (DocumentCreditReservation reservation : reservationRepository.findByUserIdAndStatus(
                owner, DocumentCreditReservationStatus.RESERVED)) {
            releaseLocked(reservation, wallet, "Account access revoked before document delivery");
        }
        if (wallet.getLifecycleStatus() != DocumentCreditWalletStatus.REVOKED) {
            wallet.setLifecycleStatus(DocumentCreditWalletStatus.REVOKED);
            wallet.setRevokedAt(Instant.now());
            walletRepository.save(wallet);
        }
        return AccountPaymentLifecycleResponse.builder()
                .status("ACCESS_REVOKED_RECORDS_RETAINED")
                .accessRevokedAt(wallet.getRevokedAt())
                .financialRecordRetentionYears(properties.getRetention().getFinancialRecordYears())
                .providerReconciliationEvidenceRetained(true)
                .build();
    }

    @Transactional
    public void requireCheckoutAccess(String owner) {
        provisioner.ensureWallet(owner);
        DocumentCreditWallet wallet = walletRepository.findByUserIdForUpdate(owner).orElseThrow();
        requireSpendable(wallet);
    }

    private DocumentCreditReleaseResponse releaseLocked(
            DocumentCreditReservation reservation,
            DocumentCreditWallet wallet,
            String reason) {
        int before = wallet.getBalanceCredits();
        int after = Math.addExact(before, reservation.getDocumentCredits());
        wallet.setBalanceCredits(after);
        walletRepository.save(wallet);
        transactionRepository.save(transaction(
                wallet, DocumentCreditTransactionType.DOCUMENT_RESERVATION_RELEASED,
                reservation.getDocumentCredits(), before, after,
                "DOCUMENT_RESERVATION_RELEASE:" + reservation.getId(),
                reason, reservation.getReferenceType(), reservation.getReferenceId()));
        reservation.setStatus(DocumentCreditReservationStatus.RELEASED);
        reservation.setReleasedAt(Instant.now());
        reservation.setLastTransitionReason(reasonCode(reason));
        reservationRepository.save(reservation);
        return releaseResponse(reservation, wallet);
    }

    private int creationBalance(DocumentCreditReservation reservation) {
        return transactionRepository.findByWalletIdAndOperationIdAndTransactionType(
                        reservation.getWalletId(),
                        "DOCUMENT_RESERVATION_CREATE:" + reservation.getOperationKey(),
                        DocumentCreditTransactionType.DOCUMENT_RESERVED)
                .map(DocumentCreditTransaction::getBalanceAfter)
                .orElseThrow(() -> new IllegalStateException(
                        "Document-credit reservation is missing its creation transaction"));
    }

    private void requireSpendable(DocumentCreditWallet wallet) {
        if (wallet.getLifecycleStatus() == DocumentCreditWalletStatus.REVOKED
                || wallet.getLifecycleStatus() == DocumentCreditWalletStatus.REVOCATION_PENDING) {
            throw api(HttpStatus.FORBIDDEN, "PAYMENT_ACCESS_REVOKED",
                    "Payment and document-credit access has been revoked for this account.");
        }
        if (wallet.getLifecycleStatus() == DocumentCreditWalletStatus.BLOCKED_REVIEW) {
            throw api(HttpStatus.LOCKED, "PAYMENT_REVIEW_REQUIRED",
                    "This account requires payment review before document credits can be used.");
        }
    }

    private DocumentCreditTransaction transaction(
            DocumentCreditWallet wallet,
            DocumentCreditTransactionType type,
            int amount,
            int before,
            int after,
            String operationId,
            String description,
            String referenceType,
            String referenceId) {
        return DocumentCreditTransaction.builder()
                .userId(wallet.getUserId())
                .walletId(wallet.getId())
                .transactionType(type)
                .documentCredits(amount)
                .balanceDeltaCredits(Math.subtractExact(after, before))
                .balanceBefore(before)
                .balanceAfter(after)
                .operationId(operationId)
                .description(description)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .build();
    }

    private DocumentCreditWalletResponse mapWallet(DocumentCreditWallet wallet) {
        return DocumentCreditWalletResponse.builder()
                .balanceDocumentCredits(wallet.getBalanceCredits())
                .lifetimePurchasedDocumentCredits(wallet.getLifetimePurchasedCredits())
                .lifetimeSpentDocumentCredits(wallet.getLifetimeSpentCredits())
                .lifetimeReversedDocumentCredits(wallet.getLifetimeReversedCredits())
                .reviewDebtDocumentCredits(wallet.getReviewDebtCredits())
                .freeAllowanceGranted(wallet.isFreeAllowanceGranted())
                .status(wallet.getLifecycleStatus().name())
                .build();
    }

    private DocumentCreditTransactionResponse mapTransaction(DocumentCreditTransaction transaction) {
        DocumentCreditTransactionResponse.Type publicType = switch (
                transaction.getTransactionType()) {
            case LEGACY_VALUE_MIGRATED -> DocumentCreditTransactionResponse.Type.ADJUSTMENT;
            default -> DocumentCreditTransactionResponse.Type.valueOf(
                    transaction.getTransactionType().name());
        };
        return DocumentCreditTransactionResponse.builder()
                .id(transaction.getId())
                .type(publicType)
                .documentCredits(transaction.getDocumentCredits())
                .balanceBeforeDocumentCredits(transaction.getBalanceBefore())
                .balanceAfterDocumentCredits(transaction.getBalanceAfter())
                .operationId(transaction.getOperationId())
                .description(transaction.getDescription())
                .referenceType(transaction.getReferenceType())
                .referenceId(transaction.getReferenceId())
                .createdAt(transaction.getCreatedAt())
                .build();
    }

    private DocumentCreditReservationResponse mapReservation(
            DocumentCreditReservation reservation, int balanceAfterReservation) {
        return DocumentCreditReservationResponse.builder()
                .reservationId(reservation.getId())
                .documentCredits(reservation.getDocumentCredits())
                .balanceAfterReservation(balanceAfterReservation)
                .status(reservation.getStatus().name())
                .operationKey(reservation.getOperationKey())
                .regeneration(reservation.isRegeneration())
                .expiresAt(reservation.getExpiresAt())
                .build();
    }

    private DocumentCreditCommitResponse commitResponse(
            DocumentCreditReservation reservation, DocumentCreditWallet wallet) {
        return DocumentCreditCommitResponse.builder()
                .reservationId(reservation.getId())
                .spentDocumentCredits(reservation.getDocumentCredits())
                .status(reservation.getStatus().name())
                .wallet(mapWallet(wallet))
                .build();
    }

    private DocumentCreditReleaseResponse releaseResponse(
            DocumentCreditReservation reservation, DocumentCreditWallet wallet) {
        return DocumentCreditReleaseResponse.builder()
                .reservationId(reservation.getId())
                .releasedDocumentCredits(reservation.getDocumentCredits())
                .status(reservation.getStatus().name())
                .wallet(mapWallet(wallet))
                .build();
    }

    private String deliveredDescription(int credits) {
        return credits == 1 ? "Tailored document delivered" : "Tailored CV and cover letter delivered";
    }

    private String safeReason(String reason) {
        if (reason == null || reason.isBlank()) return "Document generation did not deliver an output";
        String trimmed = reason.trim();
        return trimmed.length() <= 128 ? trimmed : trimmed.substring(0, 128);
    }

    private String reasonCode(String reason) {
        if (reason.contains("cancel")) return "CANCELLED_NO_CHARGE";
        if (reason.contains("fallback") || reason.contains("FALLBACK")) return "FALLBACK_NO_CHARGE";
        if (reason.contains("expired")) return "EXPIRED_NO_CHARGE";
        return "FAILED_NO_CHARGE";
    }

    private PaymentApiException api(HttpStatus status, String code, String message) {
        return new PaymentApiException(status, code, message);
    }
}
