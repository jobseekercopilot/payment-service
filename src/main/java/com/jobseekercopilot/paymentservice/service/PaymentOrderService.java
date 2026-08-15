package com.jobseekercopilot.paymentservice.service;

import com.jobseekercopilot.paymentservice.config.PaymentProperties;
import com.jobseekercopilot.paymentservice.dto.BindCheckoutSessionRequest;
import com.jobseekercopilot.paymentservice.dto.CheckoutReadinessResponse;
import com.jobseekercopilot.paymentservice.dto.CreatePaymentOrderRequest;
import com.jobseekercopilot.paymentservice.dto.PaymentOrderResponse;
import com.jobseekercopilot.paymentservice.dto.PaymentOrderStatusResponse;
import com.jobseekercopilot.paymentservice.dto.ProviderPaymentEventRequest;
import com.jobseekercopilot.paymentservice.dto.ProviderPaymentEventResponse;
import com.jobseekercopilot.paymentservice.dto.ProviderCheckoutSessionReference;
import com.jobseekercopilot.paymentservice.entity.DocumentCreditTransaction;
import com.jobseekercopilot.paymentservice.entity.DocumentCreditTransactionType;
import com.jobseekercopilot.paymentservice.entity.DocumentCreditWallet;
import com.jobseekercopilot.paymentservice.entity.DocumentCreditWalletStatus;
import com.jobseekercopilot.paymentservice.entity.FoundingPromotionCampaign;
import com.jobseekercopilot.paymentservice.entity.FoundingPromotionReservation;
import com.jobseekercopilot.paymentservice.entity.PaymentOrder;
import com.jobseekercopilot.paymentservice.entity.PaymentOrderStatus;
import com.jobseekercopilot.paymentservice.entity.PaymentProviderEvent;
import com.jobseekercopilot.paymentservice.entity.PromotionReservationStatus;
import com.jobseekercopilot.paymentservice.exception.PaymentApiException;
import com.jobseekercopilot.paymentservice.repository.DocumentCreditTransactionRepository;
import com.jobseekercopilot.paymentservice.repository.DocumentCreditWalletRepository;
import com.jobseekercopilot.paymentservice.repository.FoundingPromotionCampaignRepository;
import com.jobseekercopilot.paymentservice.repository.FoundingPromotionReservationRepository;
import com.jobseekercopilot.paymentservice.repository.PaymentOrderRepository;
import com.jobseekercopilot.paymentservice.repository.PaymentProviderEventIngestLockRepository;
import com.jobseekercopilot.paymentservice.repository.PaymentProviderEventRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentOrderService {
    private static final String ACCOUNT_ACCESS_REVOKED = "ACCOUNT_ACCESS_REVOKED";
    private static final String PROVIDER_EXPIRY_PENDING =
            "ACCOUNT_ACCESS_REVOKED_PROVIDER_EXPIRY_PENDING";
    private static final String PROVIDER_EXPIRED =
            "ACCOUNT_ACCESS_REVOKED_PROVIDER_EXPIRED";
    private static final List<PaymentOrderStatus> PRIOR_PAID_STATUSES = List.of(
            PaymentOrderStatus.FULFILLED,
            PaymentOrderStatus.REFUNDED,
            PaymentOrderStatus.PARTIALLY_REFUNDED,
            PaymentOrderStatus.DISPUTED,
            PaymentOrderStatus.MANUAL_REVIEW);

    private final PaymentOrderRepository orderRepository;
    private final PaymentProviderEventRepository eventRepository;
    private final PaymentProviderEventIngestLockRepository eventIngestLockRepository;
    private final FoundingPromotionCampaignRepository campaignRepository;
    private final FoundingPromotionReservationRepository promotionRepository;
    private final DocumentCreditWalletRepository walletRepository;
    private final DocumentCreditTransactionRepository transactionRepository;
    private final DocumentCreditWalletProvisioner walletProvisioner;
    private final DocumentCreditService documentCreditService;
    private final PaymentProperties properties;

    public CheckoutReadinessResponse readiness() {
        boolean ready = documentCreditService.checkoutReady();
        return CheckoutReadinessResponse.builder()
                .checkoutAvailable(ready)
                .code(ready ? "READY" : readinessCode())
                .mode(properties.getCheckout().isProviderLiveModeExpected() ? "LIVE" : "TEST")
                .build();
    }

    @Transactional
    public PaymentOrderResponse createOrder(
            String owner, String idempotencyKey, CreatePaymentOrderRequest request) {
        requireCheckoutReady();
        documentCreditService.requireCheckoutAccess(owner);
        String key = normaliseIdempotencyKey(idempotencyKey);
        requireConsumerAcknowledgements(request);
        walletProvisioner.ensureWallet(owner);
        DocumentCreditWallet checkoutWallet = walletRepository.findByUserIdForUpdate(owner)
                .orElseThrow();
        if (checkoutWallet.getLifecycleStatus() != DocumentCreditWalletStatus.ACTIVE) {
            throw api(HttpStatus.FORBIDDEN, "PAYMENT_ACCESS_REVOKED",
                    "Checkout is not available for this account.");
        }
        PaymentOrder existing = orderRepository.findByUserIdAndIdempotencyKey(owner, key).orElse(null);
        if (existing != null) {
            requireSameOrderRequest(existing, request);
            return response(existing);
        }
        if (!properties.getBillingCountry().equals(request.getBillingCountry())) {
            throw api(HttpStatus.UNPROCESSABLE_ENTITY, "COUNTRY_NOT_SUPPORTED",
                    "The public beta currently supports GB billing addresses only.");
        }
        PaymentProperties.PricingPlan plan = properties.activePricingPlan(request.getPricingPlanId())
                .orElseThrow(() -> api(HttpStatus.UNPROCESSABLE_ENTITY, "PLAN_NOT_AVAILABLE",
                        "The selected document-credit pack is not available."));
        if (plan.getDocumentCredits() < 1 || plan.getPriceGbpPence() < 1) {
            throw new IllegalStateException("The server-owned payment catalog is invalid");
        }
        Instant now = Instant.now();
        PaymentOrder order = orderRepository.saveAndFlush(PaymentOrder.builder()
                .userId(owner)
                .idempotencyKey(key)
                .status(PaymentOrderStatus.PENDING_CHECKOUT)
                .catalogVersion(properties.getCatalogVersion())
                .pricingPlanId(plan.getId())
                .pricingPlanName(plan.getName())
                .baseDocumentCredits(plan.getDocumentCredits())
                .promotionBonusCredits(0)
                .priceMinor(plan.getPriceGbpPence())
                .currency(properties.getCurrency())
                .billingCountryIntent(properties.getBillingCountry())
                .taxTreatment(properties.getTaxTreatment())
                .taxStatus(properties.getTaxStatus())
                .legalEntityType(properties.getLegalEntity().getType())
                .legalEntityConfigurationVersion(
                        properties.getLegalEntity().getConfigurationVersion())
                .consumerTermsVersion(properties.getConsumerTerms().getVersion())
                .immediateSupplyRequested(true)
                .cancellationRightLossAcknowledged(true)
                .consumerTermsAcceptedAt(now)
                .reversedDocumentCredits(0)
                .reviewShortfallCredits(0)
                .expiresAt(now.plus(properties.getCheckout().getOrderTtl()))
                .build());
        int guaranteedBonus = reservePromotion(order);
        if (guaranteedBonus > 0) {
            order.setPromotionBonusCredits(guaranteedBonus);
            order = orderRepository.save(order);
        }
        return response(order);
    }

    @Transactional(readOnly = true)
    public PaymentOrderResponse order(String owner, UUID orderId) {
        return response(orderRepository.findByIdAndUserId(orderId, owner)
                .orElseThrow(() -> api(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND",
                        "The payment order was not found.")));
    }

    @Transactional(readOnly = true)
    public PaymentOrderStatusResponse orderStatus(String owner, UUID orderId) {
        PaymentOrder order = orderRepository.findByIdAndUserId(orderId, owner)
                .orElseThrow(() -> api(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND",
                        "The payment order was not found."));
        boolean fulfilled = order.getStatus() == PaymentOrderStatus.FULFILLED;
        int total = fulfilled ? Math.addExact(
                order.getBaseDocumentCredits(), order.getPromotionBonusCredits()) : 0;
        return PaymentOrderStatusResponse.builder()
                .orderId(order.getId())
                .status(order.getStatus())
                .pricingPlanId(order.getPricingPlanId())
                .documentCredits(order.getBaseDocumentCredits())
                .promotionBonusDocumentCredits(order.getPromotionBonusCredits())
                .totalGrantedDocumentCredits(total)
                .priceMinor(order.getPriceMinor())
                .currency(order.getCurrency())
                .taxTreatment(order.getTaxTreatment())
                .taxStatus(order.getTaxStatus())
                .legalEntityType(order.getLegalEntityType())
                .legalEntityConfigurationVersion(
                        order.getLegalEntityConfigurationVersion())
                .createdAt(order.getCreatedAt())
                .expiresAt(order.getExpiresAt())
                .fulfilledAt(order.getFulfilledAt())
                .creditsAdded(fulfilled)
                .messageCode(statusMessage(order.getStatus()))
                .build();
    }

    @Transactional
    public PaymentOrderResponse bindCheckout(
            String owner, UUID orderId, BindCheckoutSessionRequest request) {
        PaymentOrder order = lockedOwnedOrder(owner, orderId);
        if (order.getStripeSessionId() != null) {
            if (!order.getStripeSessionId().equals(request.getStripeSessionId())) {
                throw api(HttpStatus.CONFLICT, "ORDER_ALREADY_BOUND",
                        "The payment order is already bound to a different Checkout session.");
            }
            return response(order);
        }
        if (order.getStatus() != PaymentOrderStatus.PENDING_CHECKOUT
                || !order.getExpiresAt().isAfter(Instant.now())) {
            throw api(HttpStatus.CONFLICT, "ORDER_NOT_OPEN",
                    "The payment order is no longer available for Checkout.");
        }
        order.setStripeSessionId(request.getStripeSessionId());
        order.setStatus(PaymentOrderStatus.CHECKOUT_OPEN);
        order.setCheckoutBoundAt(Instant.now());
        return response(orderRepository.save(order));
    }

    @Transactional
    public PaymentOrderResponse cancelOrder(String owner, UUID orderId) {
        PaymentOrder order = lockedOwnedOrder(owner, orderId);
        if (order.getStatus() == PaymentOrderStatus.CANCELLED
                || order.getStatus() == PaymentOrderStatus.EXPIRED) {
            return response(order);
        }
        if (order.getStripeSessionId() != null
                || order.getStatus() != PaymentOrderStatus.PENDING_CHECKOUT) {
            throw api(HttpStatus.CONFLICT, "ORDER_CANNOT_BE_CANCELLED",
                    "A bound or settled payment order cannot be cancelled locally.");
        }
        order.setStatus(PaymentOrderStatus.CANCELLED);
        releasePromotion(order, "CHECKOUT_CREATION_CANCELLED");
        return response(orderRepository.save(order));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean reconcileExpiredOrder(UUID orderId, Instant reconciliationTime) {
        PaymentOrder order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null
                || (order.getStatus() != PaymentOrderStatus.PENDING_CHECKOUT
                        && order.getStatus() != PaymentOrderStatus.CHECKOUT_OPEN)
                || order.getExpiresAt().isAfter(reconciliationTime)) {
            return false;
        }
        order.setStatus(PaymentOrderStatus.EXPIRED);
        releasePromotion(order, "CHECKOUT_EXPIRED_RECOVERY");
        orderRepository.save(order);
        return true;
    }

    @Transactional
    public CheckoutRevocationResult revokeOpenCheckoutAccess(String owner) {
        int revoked = 0;
        java.util.ArrayList<ProviderCheckoutSessionReference> providerSessions =
                new java.util.ArrayList<>();
        for (PaymentOrder candidate : orderRepository.findByUserIdAndStatusIn(
                owner, List.of(PaymentOrderStatus.PENDING_CHECKOUT, PaymentOrderStatus.CHECKOUT_OPEN,
                        PaymentOrderStatus.CANCELLED))) {
            PaymentOrder order = orderRepository.findByIdForUpdate(candidate.getId()).orElse(null);
            if (order == null) {
                continue;
            }
            if (order.getStatus() == PaymentOrderStatus.CANCELLED) {
                if (PROVIDER_EXPIRY_PENDING.equals(order.getManualReviewReason())
                        && order.getStripeSessionId() != null) {
                    providerSessions.add(new ProviderCheckoutSessionReference(
                            order.getId(), order.getStripeSessionId()));
                }
                continue;
            }
            if (order.getStatus() != PaymentOrderStatus.PENDING_CHECKOUT
                    && order.getStatus() != PaymentOrderStatus.CHECKOUT_OPEN) {
                continue;
            }
            if (order.getStripeSessionId() != null) {
                providerSessions.add(new ProviderCheckoutSessionReference(
                        order.getId(), order.getStripeSessionId()));
            }
            order.setStatus(PaymentOrderStatus.CANCELLED);
            releasePromotion(order, "ACCOUNT_ACCESS_REVOKED");
            order.setManualReviewReason(order.getStripeSessionId() == null
                    ? ACCOUNT_ACCESS_REVOKED : PROVIDER_EXPIRY_PENDING);
            orderRepository.save(order);
            revoked++;
        }
        return new CheckoutRevocationResult(revoked, List.copyOf(providerSessions));
    }

    public record CheckoutRevocationResult(
            int revokedOrders, List<ProviderCheckoutSessionReference> providerSessionsToExpire) {}

    @Transactional
    public void confirmProviderSessionExpired(
            String owner, ProviderCheckoutSessionReference reference) {
        PaymentOrder order = orderRepository.findByIdForUpdate(reference.orderId())
                .orElseThrow(() -> api(HttpStatus.NOT_FOUND, "PAYMENT_ORDER_NOT_FOUND",
                        "Payment order was not found."));
        if (!owner.equals(order.getUserId())
                || !reference.providerSessionId().equals(order.getStripeSessionId())) {
            throw api(HttpStatus.BAD_REQUEST, "PROVIDER_SESSION_OWNER_MISMATCH",
                    "Provider Checkout session does not match the owned order.");
        }
        if (PROVIDER_EXPIRED.equals(order.getManualReviewReason())) {
            return;
        }
        if (order.getStatus() != PaymentOrderStatus.CANCELLED
                || !PROVIDER_EXPIRY_PENDING.equals(order.getManualReviewReason())) {
            throw api(HttpStatus.CONFLICT, "PROVIDER_SESSION_EXPIRY_NOT_PENDING",
                    "Provider Checkout session is not pending lifecycle expiry.");
        }
        order.setManualReviewReason(PROVIDER_EXPIRED);
        orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public List<ProviderCheckoutSessionReference> pendingProviderSessionExpiries(String owner) {
        return orderRepository.findByUserIdAndStatusIn(
                        owner, List.of(PaymentOrderStatus.CANCELLED)).stream()
                .filter(order -> PROVIDER_EXPIRY_PENDING.equals(order.getManualReviewReason()))
                .filter(order -> order.getStripeSessionId() != null)
                .map(order -> new ProviderCheckoutSessionReference(
                        order.getId(), order.getStripeSessionId()))
                .toList();
    }

    public List<UUID> pendingProviderSessionExpiryIds(int batchSize) {
        return orderRepository.findProviderSessionExpiryPendingIds(
                PaymentOrderStatus.CANCELLED,
                PROVIDER_EXPIRY_PENDING,
                org.springframework.data.domain.PageRequest.of(0, batchSize));
    }

    @Transactional(readOnly = true)
    public PendingProviderSession pendingProviderSessionExpiry(UUID orderId) {
        PaymentOrder order = orderRepository.findById(orderId).orElse(null);
        if (order == null
                || order.getStatus() != PaymentOrderStatus.CANCELLED
                || !PROVIDER_EXPIRY_PENDING.equals(order.getManualReviewReason())
                || order.getStripeSessionId() == null) {
            return null;
        }
        return new PendingProviderSession(
                order.getUserId(),
                new ProviderCheckoutSessionReference(order.getId(), order.getStripeSessionId()));
    }

    public record PendingProviderSession(
            String owner, ProviderCheckoutSessionReference reference) {}

    @Transactional
    public ProviderPaymentEventResponse providerEvent(ProviderPaymentEventRequest request) {
        return processProviderEvent(request);
    }

    private ProviderPaymentEventResponse processProviderEvent(ProviderPaymentEventRequest request) {
        eventIngestLockRepository.findByIdForUpdate(1).orElseThrow(() ->
                new IllegalStateException("Provider event ingest lock is missing"));
        PaymentProviderEvent replay = eventRepository.findByProviderAndProviderEventId(
                "STRIPE", request.getProviderEventId()).orElse(null);
        if (replay != null) {
            if (!replay.getPayloadSha256().equals(request.getPayloadSha256())) {
                throw api(HttpStatus.CONFLICT, "PROVIDER_EVENT_MISMATCH",
                        "The provider event ID was replayed with different evidence.");
            }
            PaymentOrder replayOrder = replay.getOrderId() == null
                    ? null : orderRepository.findById(replay.getOrderId()).orElse(null);
            return eventResponse(replay, replayOrder, 0, 0);
        }
        UUID resolvedId = resolveOrderId(request);
        PaymentOrder order = resolvedId == null ? null
                : orderRepository.findByIdForUpdate(resolvedId).orElseThrow();
        PaymentProviderEvent lockedReplay = eventRepository.findByProviderAndProviderEventId(
                "STRIPE", request.getProviderEventId()).orElse(null);
        if (lockedReplay != null) {
            if (!lockedReplay.getPayloadSha256().equals(request.getPayloadSha256())) {
                throw api(HttpStatus.CONFLICT, "PROVIDER_EVENT_MISMATCH",
                        "The provider event ID was replayed with different evidence.");
            }
            return eventResponse(lockedReplay, order, 0, 0);
        }
        PaymentProviderEvent event = PaymentProviderEvent.builder()
                .provider("STRIPE")
                .providerEventId(request.getProviderEventId())
                .eventType(request.getEventType())
                .orderId(order == null ? null : order.getId())
                .payloadSha256(request.getPayloadSha256())
                .providerObjectId(firstNonBlank(
                        request.getPaymentIntentId(), request.getStripeSessionId()))
                .amountMinor(request.getReversalAmountMinor() == null
                        ? request.getAmountTotalMinor() : request.getReversalAmountMinor())
                .currency(upper(request.getCurrency()))
                .billingCountry(upper(request.getBillingCountry()))
                .providerLivemode(request.getLiveMode())
                .providerCreatedAt(request.getEventCreatedAt())
                .outcome("RECEIVED")
                .build();

        ProviderPaymentEventResponse result;
        switch (request.getEventType()) {
            case "checkout.session.completed" -> result = completeCheckout(order, request, event);
            case "checkout.session.expired" -> result = expireCheckout(order, request, event);
            case "charge.refunded" -> result = reverse(order, request, event, false);
            case "charge.dispute.created" -> result = reverse(order, request, event, true);
            default -> {
                event.setOutcome("IGNORED_EVENT_TYPE");
                event.setProcessedAt(Instant.now());
                eventRepository.save(event);
                result = eventResponse(event, order, 0, 0);
            }
        }
        return result;
    }

    private ProviderPaymentEventResponse completeCheckout(
            PaymentOrder order,
            ProviderPaymentEventRequest request,
            PaymentProviderEvent event) {
        if (order == null) {
            throw api(HttpStatus.UNPROCESSABLE_ENTITY, "ORDER_NOT_FOUND",
                    "The settled Checkout session is not linked to an owned order.");
        }
        if (order.getStatus() == PaymentOrderStatus.FULFILLED) {
            event.setOutcome("ALREADY_FULFILLED");
            event.setProcessedAt(Instant.now());
            eventRepository.save(event);
            return eventResponse(event, order, 0, 0);
        }
        String mismatch = settlementMismatch(order, request);
        if (mismatch != null) {
            order.setStatus(PaymentOrderStatus.MANUAL_REVIEW);
            order.setManualReviewReason(mismatch);
            event.setOutcome("UNFULFILLED_MANUAL_REVIEW");
            event.setProcessedAt(Instant.now());
            orderRepository.save(order);
            eventRepository.save(event);
            return eventResponse(event, order, 0, 0);
        }
        DocumentCreditWallet wallet = walletRepository.findByUserIdForUpdate(order.getUserId())
                .orElseThrow();
        if (wallet.getLifecycleStatus() != DocumentCreditWalletStatus.ACTIVE) {
            order.setStatus(PaymentOrderStatus.MANUAL_REVIEW);
            order.setManualReviewReason("ACCOUNT_NOT_ACTIVE");
            event.setOutcome("UNFULFILLED_MANUAL_REVIEW");
            event.setProcessedAt(Instant.now());
            orderRepository.save(order);
            eventRepository.save(event);
            return eventResponse(event, order, 0, 0);
        }
        int granted = order.getBaseDocumentCredits();
        int before = wallet.getBalanceCredits();
        int afterBase = Math.addExact(before, order.getBaseDocumentCredits());
        transactionRepository.save(transaction(
                wallet, DocumentCreditTransactionType.PURCHASE,
                order.getBaseDocumentCredits(), before, afterBase,
                "PAYMENT_ORDER_PURCHASE:" + order.getId(),
                order.getPricingPlanName() + " document credits purchased",
                "PAYMENT_ORDER", order.getId().toString()));
        int after = afterBase;
        if (order.getPromotionBonusCredits() > 0) {
            FoundingPromotionReservation promotion = promotionRepository.findByOrderId(order.getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Guaranteed founding bonus is missing its reservation"));
            if (promotion.getStatus() != PromotionReservationStatus.RESERVED) {
                throw new IllegalStateException(
                        "Guaranteed founding bonus is not reserved at settlement");
            }
            completePromotion(promotion);
            after = Math.addExact(afterBase, order.getPromotionBonusCredits());
            transactionRepository.save(transaction(
                    wallet, DocumentCreditTransactionType.PROMOTION_BONUS,
                    order.getPromotionBonusCredits(), afterBase, after,
                    "FOUNDING_PROMOTION:" + order.getId(),
                    "Founding customer bonus credits granted",
                    "PAYMENT_ORDER", order.getId().toString()));
            granted = Math.addExact(granted, order.getPromotionBonusCredits());
        }
        wallet.setBalanceCredits(after);
        wallet.setLifetimePurchasedCredits(Math.addExact(
                wallet.getLifetimePurchasedCredits(), granted));
        walletRepository.save(wallet);
        order.setStripePaymentIntentId(request.getPaymentIntentId());
        order.setProviderLivemode(request.getLiveMode());
        order.setProviderBillingCountry(upper(request.getBillingCountry()));
        order.setProviderAmountTotalMinor(request.getAmountTotalMinor());
        order.setProviderCurrency(upper(request.getCurrency()));
        order.setPaidAt(request.getEventCreatedAt() == null ? Instant.now() : request.getEventCreatedAt());
        order.setFulfilledAt(Instant.now());
        order.setStatus(PaymentOrderStatus.FULFILLED);
        event.setOutcome("FULFILLED");
        event.setProcessedAt(Instant.now());
        orderRepository.save(order);
        eventRepository.save(event);
        int reconciledReversals = reconcileEarlyReversals(order);
        if (reconciledReversals > 0) {
            event.setOutcome("FULFILLED_RECONCILED_REVERSAL");
            eventRepository.save(event);
        }
        return eventResponse(event, order, granted, 0);
    }

    private ProviderPaymentEventResponse expireCheckout(
            PaymentOrder order,
            ProviderPaymentEventRequest request,
            PaymentProviderEvent event) {
        if (order == null) {
            event.setOutcome("UNMATCHED_EXPIRED_SESSION");
        } else if (order.getStatus() == PaymentOrderStatus.CHECKOUT_OPEN
                || order.getStatus() == PaymentOrderStatus.PENDING_CHECKOUT) {
            requireSessionMatch(order, request.getStripeSessionId());
            order.setStatus(PaymentOrderStatus.EXPIRED);
            releasePromotion(order, "CHECKOUT_SESSION_EXPIRED");
            orderRepository.save(order);
            event.setOutcome("ORDER_EXPIRED");
        } else {
            event.setOutcome("TERMINAL_ORDER_UNCHANGED");
        }
        event.setProcessedAt(Instant.now());
        eventRepository.save(event);
        return eventResponse(event, order, 0, 0);
    }

    private ProviderPaymentEventResponse reverse(
            PaymentOrder order,
            ProviderPaymentEventRequest request,
            PaymentProviderEvent event,
            boolean dispute) {
        if (order == null || order.getFulfilledAt() == null) {
            event.setOutcome("UNMATCHED_REVERSAL_MANUAL_REVIEW");
            event.setProcessedAt(Instant.now());
            eventRepository.save(event);
            return eventResponse(event, order, 0, 0);
        }
        String evidenceMismatch = reversalMismatch(order, request);
        if (evidenceMismatch != null) {
            order.setStatus(PaymentOrderStatus.MANUAL_REVIEW);
            order.setManualReviewReason(evidenceMismatch);
            orderRepository.save(order);
            event.setOutcome("REVERSAL_EVIDENCE_MANUAL_REVIEW");
            event.setProcessedAt(Instant.now());
            eventRepository.save(event);
            return eventResponse(event, order, 0, 0);
        }
        int originallyGranted = Math.addExact(
                order.getBaseDocumentCredits(), order.getPromotionBonusCredits());
        int targetReversal = dispute
                ? originallyGranted
                : proportionalReversal(
                        originallyGranted,
                        request.getReversalAmountMinor(),
                        order.getPriceMinor());
        int newReversal = Math.max(0, targetReversal - order.getReversedDocumentCredits());
        DocumentCreditWallet wallet = walletRepository.findByUserIdForUpdate(order.getUserId())
                .orElseThrow();
        int removed = Math.min(wallet.getBalanceCredits(), newReversal);
        int shortfall = Math.subtractExact(newReversal, removed);
        int before = wallet.getBalanceCredits();
        int after = Math.subtractExact(before, removed);
        if (newReversal > 0) {
            transactionRepository.save(transaction(
                    wallet,
                    dispute ? DocumentCreditTransactionType.DISPUTE_REVERSAL
                            : DocumentCreditTransactionType.REFUND_REVERSAL,
                    newReversal, before, after,
                    (dispute ? "PAYMENT_DISPUTE:" : "PAYMENT_REFUND:")
                            + request.getProviderEventId(),
                    dispute ? "Credits reversed after a payment dispute"
                            : "Credits reversed after a payment refund",
                    "PAYMENT_ORDER", order.getId().toString()));
            wallet.setBalanceCredits(after);
            wallet.setLifetimeReversedCredits(Math.addExact(
                    wallet.getLifetimeReversedCredits(), newReversal));
            wallet.setReviewDebtCredits(Math.addExact(wallet.getReviewDebtCredits(), shortfall));
            if (shortfall > 0) {
                wallet.setLifecycleStatus(DocumentCreditWalletStatus.BLOCKED_REVIEW);
            }
            walletRepository.save(wallet);
            order.setReversedDocumentCredits(Math.addExact(
                    order.getReversedDocumentCredits(), newReversal));
            order.setReviewShortfallCredits(Math.addExact(
                    order.getReviewShortfallCredits(), shortfall));
        }
        if (dispute) {
            order.setStatus(PaymentOrderStatus.DISPUTED);
        } else if (targetReversal >= originallyGranted) {
            order.setStatus(PaymentOrderStatus.REFUNDED);
        } else {
            order.setStatus(PaymentOrderStatus.PARTIALLY_REFUNDED);
        }
        if (shortfall > 0) order.setManualReviewReason("REVERSAL_CREDIT_SHORTFALL");
        orderRepository.save(order);
        event.setOutcome(shortfall > 0 ? "REVERSED_REVIEW_REQUIRED" : "REVERSED");
        event.setProcessedAt(Instant.now());
        eventRepository.save(event);
        return eventResponse(event, order, 0, newReversal);
    }

    private int reconcileEarlyReversals(PaymentOrder order) {
        LinkedHashMap<UUID, PaymentProviderEvent> candidates =
                new LinkedHashMap<>();
        eventRepository.findByOrderIdAndOutcomeOrderByReceivedAtAsc(
                        order.getId(), "UNMATCHED_REVERSAL_MANUAL_REVIEW")
                .forEach(candidate -> candidates.put(candidate.getId(), candidate));
        if (order.getStripePaymentIntentId() != null) {
            eventRepository
                    .findByProviderAndProviderObjectIdAndOutcomeOrderByReceivedAtAsc(
                            "STRIPE",
                            order.getStripePaymentIntentId(),
                            "UNMATCHED_REVERSAL_MANUAL_REVIEW")
                    .forEach(candidate -> candidates.put(
                            candidate.getId(), candidate));
        }
        int reconciled = 0;
        for (PaymentProviderEvent candidate : candidates.values()) {
            candidate.setOrderId(order.getId());
            ProviderPaymentEventRequest stored =
                    storedReversalRequest(candidate, order);
            reverse(
                    order,
                    stored,
                    candidate,
                    "charge.dispute.created".equals(
                            candidate.getEventType()));
            if (!"UNMATCHED_REVERSAL_MANUAL_REVIEW".equals(
                    candidate.getOutcome())) {
                reconciled++;
            }
        }
        return reconciled;
    }

    private ProviderPaymentEventRequest storedReversalRequest(
            PaymentProviderEvent event, PaymentOrder order) {
        ProviderPaymentEventRequest request =
                new ProviderPaymentEventRequest();
        request.setProviderEventId(event.getProviderEventId());
        request.setEventType(event.getEventType());
        request.setPayloadSha256(event.getPayloadSha256());
        request.setOrderId(order.getId());
        request.setPaymentIntentId(event.getProviderObjectId());
        request.setCurrency(event.getCurrency());
        request.setLiveMode(event.getProviderLivemode());
        request.setEventCreatedAt(event.getProviderCreatedAt());
        request.setReversalAmountMinor(event.getAmountMinor());
        return request;
    }

    private String reversalMismatch(
            PaymentOrder order, ProviderPaymentEventRequest request) {
        if (!Objects.equals(
                order.getStripePaymentIntentId(),
                request.getPaymentIntentId())) {
            return "REVERSAL_PAYMENT_INTENT_MISMATCH";
        }
        if (request.getCurrency() != null
                && !request.getCurrency().isBlank()
                && !order.getCurrency().equalsIgnoreCase(
                        request.getCurrency())) {
            return "REVERSAL_CURRENCY_MISMATCH";
        }
        if (!Objects.equals(
                order.getProviderLivemode(), request.getLiveMode())) {
            return "REVERSAL_PROVIDER_MODE_MISMATCH";
        }
        return null;
    }

    private int reservePromotion(PaymentOrder order) {
        PaymentProperties.Promotion promotion = properties.getPromotion();
        if (!promotion.isEnabled() || !promotion.isReleaseAuthorised()) return 0;
        FoundingPromotionCampaign campaign = campaignRepository
                .findByIdForUpdate(promotion.getId()).orElse(null);
        if (campaign == null || !campaign.isEnabled()) return 0;
        if (orderRepository.existsByUserIdAndStatusIn(order.getUserId(), PRIOR_PAID_STATUSES)
                || promotionRepository.findFirstByUserIdAndStatusIn(
                        order.getUserId(),
                        List.of(PromotionReservationStatus.RESERVED,
                                PromotionReservationStatus.COMPLETED)).isPresent()) {
            return 0;
        }
        if (campaign.getActiveReservations() + campaign.getCompletedClaims()
                >= campaign.getCustomerLimit()) return 0;
        int bonus = documentCreditService.promotionBonus(order.getBaseDocumentCredits());
        promotionRepository.save(FoundingPromotionReservation.builder()
                .campaignId(campaign.getId())
                .orderId(order.getId())
                .userId(order.getUserId())
                .bonusDocumentCredits(bonus)
                .status(PromotionReservationStatus.RESERVED)
                .expiresAt(order.getExpiresAt())
                .build());
        campaign.setActiveReservations(Math.addExact(campaign.getActiveReservations(), 1));
        campaignRepository.save(campaign);
        return bonus;
    }

    private void completePromotion(FoundingPromotionReservation reservation) {
        FoundingPromotionCampaign campaign = campaignRepository
                .findByIdForUpdate(reservation.getCampaignId()).orElseThrow();
        if (promotionRepository.existsByCompletedOwnerKey(reservation.getUserId())) {
            throw new IllegalStateException("Founding promotion owner was already completed");
        }
        reservation.setStatus(PromotionReservationStatus.COMPLETED);
        reservation.setCompletedOwnerKey(reservation.getUserId());
        reservation.setCompletedAt(Instant.now());
        promotionRepository.save(reservation);
        campaign.setActiveReservations(Math.subtractExact(campaign.getActiveReservations(), 1));
        campaign.setCompletedClaims(Math.addExact(campaign.getCompletedClaims(), 1));
        campaignRepository.save(campaign);
    }

    private void releasePromotion(PaymentOrder order, String reason) {
        FoundingPromotionReservation reservation = promotionRepository.findByOrderId(order.getId()).orElse(null);
        if (reservation == null || reservation.getStatus() != PromotionReservationStatus.RESERVED) return;
        FoundingPromotionCampaign campaign = campaignRepository
                .findByIdForUpdate(reservation.getCampaignId()).orElseThrow();
        reservation.setStatus(PromotionReservationStatus.RELEASED);
        reservation.setReleasedAt(Instant.now());
        promotionRepository.save(reservation);
        campaign.setActiveReservations(Math.subtractExact(campaign.getActiveReservations(), 1));
        campaignRepository.save(campaign);
        order.setManualReviewReason(reason);
    }

    private String settlementMismatch(PaymentOrder order, ProviderPaymentEventRequest request) {
        if (order.getStatus() != PaymentOrderStatus.CHECKOUT_OPEN) return "ORDER_NOT_OPEN";
        if (!Objects.equals(order.getStripeSessionId(), request.getStripeSessionId())) return "SESSION_MISMATCH";
        if (!"paid".equalsIgnoreCase(request.getPaymentStatus())) return "PAYMENT_NOT_SETTLED";
        if (!"complete".equalsIgnoreCase(request.getCheckoutStatus())) return "CHECKOUT_NOT_COMPLETE";
        if (!order.getCurrency().equalsIgnoreCase(request.getCurrency())) return "CURRENCY_MISMATCH";
        if (!Objects.equals(order.getPriceMinor(), request.getAmountTotalMinor())) return "AMOUNT_MISMATCH";
        if (!properties.getBillingCountry().equalsIgnoreCase(request.getBillingCountry())) return "BILLING_COUNTRY_MISMATCH";
        if (request.getLiveMode() != properties.getCheckout().isProviderLiveModeExpected()) return "PROVIDER_MODE_MISMATCH";
        if (request.getPaymentIntentId() == null || request.getPaymentIntentId().isBlank()) return "PAYMENT_INTENT_MISSING";
        return null;
    }

    private UUID resolveOrderId(ProviderPaymentEventRequest request) {
        if (request.getOrderId() != null) {
            return orderRepository.existsById(request.getOrderId()) ? request.getOrderId() : null;
        }
        if (request.getStripeSessionId() != null && !request.getStripeSessionId().isBlank()) {
            return orderRepository.findIdByStripeSessionId(request.getStripeSessionId()).orElse(null);
        }
        if (request.getPaymentIntentId() != null && !request.getPaymentIntentId().isBlank()) {
            return orderRepository.findIdByStripePaymentIntentId(request.getPaymentIntentId()).orElse(null);
        }
        return null;
    }

    private void requireSessionMatch(PaymentOrder order, String sessionId) {
        if (!Objects.equals(order.getStripeSessionId(), sessionId)) {
            throw api(HttpStatus.CONFLICT, "PROVIDER_EVENT_MISMATCH",
                    "The provider session does not match its owned payment order.");
        }
    }

    private int proportionalReversal(int credits, Long refundedMinor, long paidMinor) {
        if (refundedMinor == null || refundedMinor < 1 || refundedMinor > paidMinor) {
            throw api(HttpStatus.UNPROCESSABLE_ENTITY, "PROVIDER_EVENT_MISMATCH",
                    "The provider refund amount is invalid for this order.");
        }
        return Math.toIntExact(
                Math.multiplyExact((long) credits, refundedMinor) / paidMinor);
    }

    private PaymentOrder lockedOwnedOrder(String owner, UUID id) {
        PaymentOrder order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> api(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND",
                        "The payment order was not found."));
        if (!order.getUserId().equals(owner)) {
            throw api(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND",
                    "The payment order was not found.");
        }
        return order;
    }

    private void requireSameOrderRequest(PaymentOrder order, CreatePaymentOrderRequest request) {
        if (!order.getPricingPlanId().equals(request.getPricingPlanId())
                || !order.getBillingCountryIntent().equals(request.getBillingCountry())
                || !Boolean.TRUE.equals(request.getImmediateSupplyRequested())
                || !Boolean.TRUE.equals(request.getCancellationRightLossAcknowledged())) {
            throw api(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT",
                    "The checkout idempotency key was already used for a different request.");
        }
    }

    private void requireConsumerAcknowledgements(CreatePaymentOrderRequest request) {
        if (!Boolean.TRUE.equals(request.getImmediateSupplyRequested())
                || !Boolean.TRUE.equals(request.getCancellationRightLossAcknowledged())) {
            throw api(HttpStatus.UNPROCESSABLE_ENTITY,
                    "CONSUMER_ACKNOWLEDGEMENTS_REQUIRED",
                    "Checkout requires express immediate-supply and cancellation-right acknowledgements.");
        }
    }

    private String normaliseIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw api(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED",
                    "Idempotency-Key is required for Checkout.");
        }
        String value = key.trim();
        if (value.length() > 128 || !value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw api(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_INVALID",
                    "Idempotency-Key has an invalid format.");
        }
        return value;
    }

    private void requireCheckoutReady() {
        if (!documentCreditService.checkoutReady()) {
            throw api(HttpStatus.SERVICE_UNAVAILABLE, readinessCode(),
                    "Checkout is not enabled for this release.");
        }
    }

    private String readinessCode() {
        if (!properties.getCheckout().isEnabled()) return "PAYMENTS_DISABLED";
        if (!properties.getCheckout().isReleaseAuthorised()) return "LIVE_RELEASE_NOT_AUTHORISED";
        if (properties.getTaxStatus() == PaymentProperties.TaxStatus.NOT_CONFIGURED) {
            return "TAX_STATUS_NOT_CONFIGURED";
        }
        if (properties.getLegalEntity().getType()
                == PaymentProperties.LegalEntityType.NOT_CONFIGURED
                || !properties.getLegalEntity().isReviewed()
                || properties.getLegalEntity().getConfigurationVersion() == null
                || properties.getLegalEntity().getConfigurationVersion().isBlank()) {
            return "LEGAL_ENTITY_NOT_CONFIGURED";
        }
        return "PROVIDER_UNAVAILABLE";
    }

    private PaymentOrderResponse response(PaymentOrder order) {
        return PaymentOrderResponse.builder()
                .orderId(order.getId())
                .status(order.getStatus())
                .ownerId(order.getUserId())
                .catalogVersion(order.getCatalogVersion())
                .pricingPlanId(order.getPricingPlanId())
                .pricingPlanName(order.getPricingPlanName())
                .documentCredits(order.getBaseDocumentCredits())
                .promotionBonusDocumentCredits(order.getPromotionBonusCredits())
                .promotionGuaranteed(order.getPromotionBonusCredits() > 0)
                .priceMinor(order.getPriceMinor())
                .currency(order.getCurrency())
                .billingCountry(order.getBillingCountryIntent())
                .taxTreatment(order.getTaxTreatment())
                .taxStatus(order.getTaxStatus())
                .legalEntityType(order.getLegalEntityType())
                .legalEntityConfigurationVersion(
                        order.getLegalEntityConfigurationVersion())
                .displayedPriceIsCheckoutTotal(true)
                .consumerTermsVersion(order.getConsumerTermsVersion())
                .consumerAcknowledgementsRecorded(
                        order.isImmediateSupplyRequested()
                                && order.isCancellationRightLossAcknowledged())
                .consumerTermsAcceptedAt(order.getConsumerTermsAcceptedAt())
                .expiresAt(order.getExpiresAt())
                .stripeSessionId(order.getStripeSessionId())
                .build();
    }

    private ProviderPaymentEventResponse eventResponse(
            PaymentProviderEvent event, PaymentOrder order, int granted, int reversed) {
        return ProviderPaymentEventResponse.builder()
                .providerEventId(event.getProviderEventId())
                .outcome(event.getOutcome())
                .orderId(order == null ? null : order.getId())
                .orderStatus(order == null ? null : order.getStatus().name())
                .grantedDocumentCredits(granted)
                .reversedDocumentCredits(reversed)
                .reviewShortfallDocumentCredits(order == null ? 0 : order.getReviewShortfallCredits())
                .build();
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

    private String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private PaymentOrderStatusResponse.MessageCode statusMessage(PaymentOrderStatus status) {
        return switch (status) {
            case PENDING_CHECKOUT, CHECKOUT_OPEN ->
                    PaymentOrderStatusResponse.MessageCode.PAYMENT_PENDING;
            case FULFILLED -> PaymentOrderStatusResponse.MessageCode.CREDITS_ADDED;
            case EXPIRED -> PaymentOrderStatusResponse.MessageCode.CHECKOUT_EXPIRED;
            case CANCELLED -> PaymentOrderStatusResponse.MessageCode.CHECKOUT_CANCELLED;
            case REFUNDED -> PaymentOrderStatusResponse.MessageCode.PAYMENT_REFUNDED;
            case PARTIALLY_REFUNDED ->
                    PaymentOrderStatusResponse.MessageCode.PAYMENT_PARTIALLY_REFUNDED;
            case DISPUTED -> PaymentOrderStatusResponse.MessageCode.PAYMENT_DISPUTED;
            case MANUAL_REVIEW ->
                    PaymentOrderStatusResponse.MessageCode.PAYMENT_REVIEW_REQUIRED;
        };
    }

    private PaymentApiException api(HttpStatus status, String code, String message) {
        return new PaymentApiException(status, code, message);
    }
}
