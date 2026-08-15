package com.jobseekercopilot.paymentservice.service;

import com.jobseekercopilot.paymentservice.config.PaymentProperties;
import com.jobseekercopilot.paymentservice.dto.AccountPaymentExportResponse;
import com.jobseekercopilot.paymentservice.dto.AccountPaymentLifecycleResponse;
import com.jobseekercopilot.paymentservice.dto.ProviderCheckoutSessionReference;
import com.jobseekercopilot.paymentservice.exception.PaymentApiException;
import com.jobseekercopilot.paymentservice.entity.DocumentCreditReservation;
import com.jobseekercopilot.paymentservice.entity.DocumentCreditTransaction;
import com.jobseekercopilot.paymentservice.entity.DocumentCreditWallet;
import com.jobseekercopilot.paymentservice.entity.PaymentOrder;
import com.jobseekercopilot.paymentservice.entity.PaymentProviderEvent;
import com.jobseekercopilot.paymentservice.repository.DocumentCreditReservationRepository;
import com.jobseekercopilot.paymentservice.repository.DocumentCreditTransactionRepository;
import com.jobseekercopilot.paymentservice.repository.DocumentCreditWalletRepository;
import com.jobseekercopilot.paymentservice.repository.PaymentOrderRepository;
import com.jobseekercopilot.paymentservice.repository.PaymentProviderEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentAccountLifecycleService {
    private final DocumentCreditService documentCreditService;
    private final PaymentOrderService paymentOrderService;
    private final DocumentCreditWalletRepository walletRepository;
    private final DocumentCreditTransactionRepository transactionRepository;
    private final DocumentCreditReservationRepository reservationRepository;
    private final PaymentOrderRepository orderRepository;
    private final PaymentProviderEventRepository eventRepository;
    private final PaymentProperties properties;
    private final ProviderSessionExpiryReconciliationService providerSessionReconciliation;

    public AccountPaymentLifecycleResponse revokeAccess(String owner) {
        AccountPaymentLifecycleResponse result = documentCreditService.revokeAccess(owner);
        PaymentOrderService.CheckoutRevocationResult checkout =
                paymentOrderService.revokeOpenCheckoutAccess(owner);
        providerSessionReconciliation.reconcile(owner, checkout.providerSessionsToExpire());
        List<ProviderCheckoutSessionReference> pending =
                paymentOrderService.pendingProviderSessionExpiries(owner);
        if (!pending.isEmpty()) {
            throw new PaymentApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "PROVIDER_SESSION_EXPIRY_PENDING",
                    "Payment access is revoked; provider Checkout expiry will be retried.");
        }
        result.setCheckoutOrdersRevoked(checkout.revokedOrders());
        result.setProviderSessionsRequireExpiry(false);
        result.setProviderCheckoutSessionsToExpire(List.of());
        return result;
    }

    @Transactional(readOnly = true)
    public AccountPaymentExportResponse export(String owner) {
        DocumentCreditWallet wallet = walletRepository.findByUserId(owner).orElse(null);
        List<DocumentCreditTransaction> transactions =
                transactionRepository.findByUserIdOrderBySequenceNumberAsc(owner);
        List<DocumentCreditReservation> reservations =
                reservationRepository.findByUserIdOrderByCreatedAtAsc(owner);
        List<PaymentOrder> orders = orderRepository.findByUserIdOrderByCreatedAtAsc(owner);
        List<UUID> orderIds = orders.stream().map(PaymentOrder::getId).toList();
        List<PaymentProviderEvent> providerEvents = orderIds.isEmpty()
                ? List.of()
                : eventRepository.findByOrderIdInOrderByReceivedAtAsc(orderIds);
        return new AccountPaymentExportResponse(
                "payment-export-v1",
                owner,
                Instant.now(),
                properties.getRetention().getFinancialRecordYears(),
                wallet == null ? null : wallet(wallet),
                transactions.stream().map(this::transaction).toList(),
                reservations.stream().map(this::reservation).toList(),
                orders.stream().map(this::order).toList(),
                providerEvents.stream().map(this::providerEvent).toList());
    }

    private AccountPaymentExportResponse.WalletRecord wallet(DocumentCreditWallet value) {
        return new AccountPaymentExportResponse.WalletRecord(
                value.getBalanceCredits(),
                value.getLifetimePurchasedCredits(),
                value.getLifetimeSpentCredits(),
                value.getLifetimeReversedCredits(),
                value.getReviewDebtCredits(),
                value.getLifecycleStatus().name(),
                value.getCreatedAt(),
                value.getRevokedAt());
    }

    private AccountPaymentExportResponse.TransactionRecord transaction(
            DocumentCreditTransaction value) {
        return new AccountPaymentExportResponse.TransactionRecord(
                value.getId(), value.getSequenceNumber(), value.getTransactionType().name(),
                value.getDocumentCredits(), value.getBalanceBefore(), value.getBalanceAfter(),
                value.getOperationId(), value.getReferenceType(), value.getReferenceId(),
                value.getCreatedAt());
    }

    private AccountPaymentExportResponse.ReservationRecord reservation(
            DocumentCreditReservation value) {
        return new AccountPaymentExportResponse.ReservationRecord(
                value.getId(), value.getOperationKey(), value.getDocumentCredits(),
                value.getStatus().name(), value.isRegeneration(), value.getReferenceType(),
                value.getReferenceId(), value.getCreatedAt(), value.getExpiresAt(),
                value.getCommittedAt(), value.getReleasedAt());
    }

    private AccountPaymentExportResponse.OrderRecord order(PaymentOrder value) {
        return new AccountPaymentExportResponse.OrderRecord(
                value.getId(), value.getStatus().name(), value.getCatalogVersion(),
                value.getPricingPlanId(), value.getBaseDocumentCredits(),
                value.getPromotionBonusCredits(), value.getReversedDocumentCredits(),
                value.getReviewShortfallCredits(), value.getPriceMinor(), value.getCurrency(),
                value.getTaxTreatment().name(), value.getTaxStatus().name(),
                value.getLegalEntityType().name(),
                value.getLegalEntityConfigurationVersion(), value.getConsumerTermsVersion(),
                value.isImmediateSupplyRequested()
                        && value.isCancellationRightLossAcknowledged(),
                value.getConsumerTermsAcceptedAt(), value.getStripeSessionId(),
                value.getStripePaymentIntentId(), value.getCreatedAt(), value.getPaidAt(),
                value.getFulfilledAt());
    }

    private AccountPaymentExportResponse.ProviderEventRecord providerEvent(
            PaymentProviderEvent value) {
        return new AccountPaymentExportResponse.ProviderEventRecord(
                value.getId(), value.getProvider(), value.getProviderEventId(),
                value.getEventType(), value.getOrderId(), value.getPayloadSha256(),
                value.getProviderObjectId(), value.getAmountMinor(), value.getCurrency(),
                value.getBillingCountry(), value.getProviderLivemode(),
                value.getProviderCreatedAt(), value.getOutcome(), value.getReceivedAt(),
                value.getProcessedAt());
    }
}
