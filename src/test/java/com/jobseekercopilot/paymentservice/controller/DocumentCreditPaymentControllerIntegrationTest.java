package com.jobseekercopilot.paymentservice.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseekercopilot.paymentservice.entity.FoundingPromotionCampaign;
import com.jobseekercopilot.paymentservice.entity.DocumentCreditReservation;
import com.jobseekercopilot.paymentservice.entity.PaymentOrder;
import com.jobseekercopilot.paymentservice.repository.DocumentCreditReservationRepository;
import com.jobseekercopilot.paymentservice.repository.DocumentCreditTransactionRepository;
import com.jobseekercopilot.paymentservice.repository.DocumentCreditWalletRepository;
import com.jobseekercopilot.paymentservice.repository.FoundingPromotionCampaignRepository;
import com.jobseekercopilot.paymentservice.repository.FoundingPromotionReservationRepository;
import com.jobseekercopilot.paymentservice.repository.PaymentOrderRepository;
import com.jobseekercopilot.paymentservice.repository.PaymentProviderEventRepository;
import com.jobseekercopilot.paymentservice.service.CommercialReservationRecoveryService;
import com.jobseekercopilot.paymentservice.service.ProviderSessionExpiryReconciliationService;
import com.jobseekercopilot.paymentservice.service.StripeLifecycleClient;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "payment.checkout.enabled=true",
        "payment.checkout.release-authorised=true",
        "payment.checkout.provider-live-mode-expected=false",
        "payment.tax-status=NOT_VAT_REGISTERED",
        "payment.legal-entity.type=SOLE_TRADER",
        "payment.legal-entity.configuration-version=test-reviewed-v1",
        "payment.legal-entity.reviewed=true",
        "payment.promotion.enabled=true",
        "payment.promotion.release-authorised=true"
})
@AutoConfigureMockMvc
class DocumentCreditPaymentControllerIntegrationTest {
    private static final String SERVICE_TOKEN = "X-Service-Token";
    private static final String OWNER = "X-Payment-Owner";
    private static final String PAYMENT_GATEWAY = "payment-gateway-test-token-0000000000000001";
    private static final String DOCUMENT_GATEWAY =
            "document-generation-gateway-test-token-00000001";
    private static final String STRIPE_GATEWAY = "stripe-gateway-test-token-0000000000000001";
    private static final String ACCOUNT_LIFECYCLE =
            "account-lifecycle-test-token-0000000000001";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired DocumentCreditReservationRepository reservationRepository;
    @Autowired DocumentCreditTransactionRepository transactionRepository;
    @Autowired DocumentCreditWalletRepository walletRepository;
    @Autowired PaymentProviderEventRepository eventRepository;
    @Autowired FoundingPromotionReservationRepository promotionReservationRepository;
    @Autowired PaymentOrderRepository orderRepository;
    @Autowired FoundingPromotionCampaignRepository campaignRepository;
    @Autowired CommercialReservationRecoveryService commercialRecoveryService;
    @Autowired ProviderSessionExpiryReconciliationService providerSessionReconciliation;
    @MockBean StripeLifecycleClient stripeLifecycleClient;

    @BeforeEach
    void clean() {
        reset(stripeLifecycleClient);
        eventRepository.deleteAll();
        promotionReservationRepository.deleteAll();
        orderRepository.deleteAll();
        reservationRepository.deleteAll();
        transactionRepository.deleteAll();
        walletRepository.deleteAll();
        FoundingPromotionCampaign campaign = campaignRepository.findById("founding-200").orElseThrow();
        campaign.setEnabled(true);
        campaign.setCustomerLimit(200);
        campaign.setActiveReservations(0);
        campaign.setCompletedClaims(0);
        campaign.setUpdatedAt(Instant.now());
        campaignRepository.saveAndFlush(campaign);
    }

    @Test
    void catalogUsesDocumentCreditsGrossPricesNeutralTaxAndExactPromotion() throws Exception {
        mockMvc.perform(get("/api/v2/payments/catalog")
                        .header(SERVICE_TOKEN, PAYMENT_GATEWAY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.catalogVersion").value("public-beta-2026-08-15"))
                .andExpect(jsonPath("$.currency").value("GBP"))
                .andExpect(jsonPath("$.billingCountry").value("GB"))
                .andExpect(jsonPath("$.taxTreatment").value("VAT_NOT_CHARGED"))
                .andExpect(jsonPath("$.taxStatus").value("NOT_VAT_REGISTERED"))
                .andExpect(jsonPath("$.displayedPriceIsCheckoutTotal").value(true))
                .andExpect(jsonPath("$.automaticRenewal").value(false))
                .andExpect(jsonPath("$.freeAllowanceCredits").value(2))
                .andExpect(jsonPath("$.plans", hasSize(3)))
                .andExpect(jsonPath("$.plans[0].id").value("starter"))
                .andExpect(jsonPath("$.plans[0].documentCredits").value(10))
                .andExpect(jsonPath("$.plans[0].priceMinor").value(799))
                .andExpect(jsonPath("$.plans[0].promotionBonusDocumentCredits").value(5))
                .andExpect(jsonPath("$.plans[1].id").value("active"))
                .andExpect(jsonPath("$.plans[1].documentCredits").value(25))
                .andExpect(jsonPath("$.plans[1].promotionBonusDocumentCredits").value(13))
                .andExpect(jsonPath("$.plans[2].promotionBonusDocumentCredits").value(30))
                .andExpect(jsonPath("$.promotion.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.plans[0].tokenAmount").doesNotExist());
    }

    @Test
    void successfulDocumentCostsExactlyOneAndFailureReleaseCostsNothing() throws Exception {
        mockMvc.perform(get("/api/v2/payments/wallet").with(paymentGateway("owner-doc")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceDocumentCredits").value(2));

        String reservation = mockMvc.perform(post("/api/v2/payments/document-credit-reservations")
                        .with(documentGateway("owner-doc"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operationKey":"generation-1:cv","documentCredits":1,
                                 "regeneration":false,"referenceType":"GENERATION_OUTPUT",
                                 "referenceId":"generation-1:CV"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceAfterReservation").value(1))
                .andReturn().getResponse().getContentAsString();
        String reservationId = objectMapper.readTree(reservation).path("reservationId").asText();

        mockMvc.perform(post("/api/v2/payments/document-credit-reservations")
                        .with(documentGateway("owner-doc"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operationKey":"generation-1:cv","documentCredits":1,
                                 "regeneration":false,"referenceType":"GENERATION_OUTPUT",
                                 "referenceId":"generation-1:CV"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(reservationId))
                .andExpect(jsonPath("$.balanceAfterReservation").value(1));

        mockMvc.perform(post("/api/v2/payments/document-credit-reservations/{id}/commit", reservationId)
                        .with(documentGateway("owner-doc")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spentDocumentCredits").value(1))
                .andExpect(jsonPath("$.wallet.balanceDocumentCredits").value(1));
        mockMvc.perform(post("/api/v2/payments/document-credit-reservations/{id}/commit", reservationId)
                        .with(documentGateway("owner-doc")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wallet.balanceDocumentCredits").value(1));

        String failed = mockMvc.perform(post("/api/v2/payments/document-credit-reservations")
                        .with(documentGateway("owner-doc"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operationKey":"generation-2:cover-letter","documentCredits":1,
                                 "regeneration":true,"referenceType":"GENERATION_OUTPUT",
                                 "referenceId":"generation-2:COVER_LETTER"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceAfterReservation").value(0))
                .andReturn().getResponse().getContentAsString();
        String failedId = objectMapper.readTree(failed).path("reservationId").asText();
        mockMvc.perform(post("/api/v2/payments/document-credit-reservations/{id}/release", failedId)
                        .with(documentGateway("owner-doc"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"DETERMINISTIC_FALLBACK_NO_CHARGE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releasedDocumentCredits").value(1))
                .andExpect(jsonPath("$.wallet.balanceDocumentCredits").value(1));

        mockMvc.perform(get("/api/v2/payments/transactions").with(paymentGateway("owner-doc")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions", hasSize(5)));
    }

    @Test
    void checkoutUsesOwnedSnapshotGuaranteesBonusAndFulfilsOnce() throws Exception {
        mockMvc.perform(get("/api/v2/payments/checkout-readiness")
                        .header(SERVICE_TOKEN, PAYMENT_GATEWAY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkoutAvailable").value(true))
                .andExpect(jsonPath("$.code").value("READY"))
                .andExpect(jsonPath("$.mode").value("TEST"));

        String orderJson = mockMvc.perform(post("/api/v2/payments/orders")
                        .with(paymentGateway("owner-pay"))
                        .header("Idempotency-Key", "checkout-click-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutRequest("active")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentCredits").value(25))
                .andExpect(jsonPath("$.priceMinor").value(1699))
                .andExpect(jsonPath("$.taxTreatment").value("VAT_NOT_CHARGED"))
                .andExpect(jsonPath("$.taxStatus").value("NOT_VAT_REGISTERED"))
                .andExpect(jsonPath("$.legalEntityType").value("SOLE_TRADER"))
                .andExpect(jsonPath("$.legalEntityConfigurationVersion")
                        .value("test-reviewed-v1"))
                .andExpect(jsonPath("$.promotionBonusDocumentCredits").value(13))
                .andExpect(jsonPath("$.promotionGuaranteed").value(true))
                .andExpect(jsonPath("$.consumerTermsVersion")
                        .value("uk-consumer-terms-2026-08-15"))
                .andExpect(jsonPath("$.consumerAcknowledgementsRecorded").value(true))
                .andReturn().getResponse().getContentAsString();
        JsonNode order = objectMapper.readTree(orderJson);
        String orderId = order.path("orderId").asText();

        mockMvc.perform(post("/api/v2/payments/orders")
                        .with(paymentGateway("owner-pay"))
                        .header("Idempotency-Key", "checkout-click-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutRequest("active")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(orderId));

        mockMvc.perform(post("/api/v2/payments/orders/{id}/bind-stripe-session", orderId)
                        .with(stripeGateway("owner-pay"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stripeSessionId\":\"cs_test_owned\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKOUT_OPEN"));

        String settled = settledEvent(
                "evt_completed_1", orderId, "cs_test_owned", "pi_test_owned", "GB");
        mockMvc.perform(post("/api/v2/payments/provider-events/stripe")
                        .header(SERVICE_TOKEN, STRIPE_GATEWAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settled))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("FULFILLED"))
                .andExpect(jsonPath("$.grantedDocumentCredits").value(38));
        mockMvc.perform(post("/api/v2/payments/provider-events/stripe")
                        .header(SERVICE_TOKEN, STRIPE_GATEWAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settled))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("FULFILLED"))
                .andExpect(jsonPath("$.grantedDocumentCredits").value(0));

        mockMvc.perform(get("/api/v2/payments/wallet").with(paymentGateway("owner-pay")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceDocumentCredits").value(40))
                .andExpect(jsonPath("$.lifetimePurchasedDocumentCredits").value(38));

        mockMvc.perform(post("/api/v2/payments/provider-events/stripe")
                        .header(SERVICE_TOKEN, STRIPE_GATEWAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundEvent(orderId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("REVERSED"))
                .andExpect(jsonPath("$.reversedDocumentCredits").value(38));
        mockMvc.perform(get("/api/v2/payments/wallet").with(paymentGateway("owner-pay")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceDocumentCredits").value(2));
    }

    @Test
    void refundArrivingBeforeCheckoutCompletionIsReconciledWithoutNetGrant()
            throws Exception {
        String orderJson = mockMvc.perform(post("/api/v2/payments/orders")
                        .with(paymentGateway("owner-early-refund"))
                        .header("Idempotency-Key", "early-refund-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutRequest("starter")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String orderId = objectMapper.readTree(orderJson)
                .path("orderId").asText();
        mockMvc.perform(post(
                        "/api/v2/payments/orders/{id}/bind-stripe-session",
                        orderId)
                        .with(stripeGateway("owner-early-refund"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stripeSessionId\":\"cs_early_refund\"}"))
                .andExpect(status().isOk());

        String earlyRefund = objectMapper.writeValueAsString(
                objectMapper.createObjectNode()
                        .put("providerEventId", "evt_early_refund")
                        .put("eventType", "charge.refunded")
                        .put("payloadSha256", "c".repeat(64))
                        .put("paymentIntentId", "pi_early_refund")
                        .put("currency", "GBP")
                        .put("liveMode", false)
                        .put("reversalAmountMinor", 799));
        mockMvc.perform(post("/api/v2/payments/provider-events/stripe")
                        .header(SERVICE_TOKEN, STRIPE_GATEWAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(earlyRefund))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome")
                        .value("UNMATCHED_REVERSAL_MANUAL_REVIEW"))
                .andExpect(jsonPath("$.reversedDocumentCredits").value(0));

        mockMvc.perform(post("/api/v2/payments/provider-events/stripe")
                        .header(SERVICE_TOKEN, STRIPE_GATEWAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settledEvent(
                                "evt_early_completed",
                                orderId,
                                "cs_early_refund",
                                "pi_early_refund",
                                "GB").replace(
                                        "\"amountTotalMinor\":1699",
                                        "\"amountTotalMinor\":799")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome")
                        .value("FULFILLED_RECONCILED_REVERSAL"))
                .andExpect(jsonPath("$.orderStatus").value("REFUNDED"))
                .andExpect(jsonPath("$.grantedDocumentCredits").value(15));

        mockMvc.perform(get("/api/v2/payments/wallet")
                        .with(paymentGateway("owner-early-refund")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceDocumentCredits").value(2))
                .andExpect(jsonPath("$.lifetimePurchasedDocumentCredits")
                        .value(15))
                .andExpect(jsonPath("$.lifetimeReversedDocumentCredits")
                        .value(15));
        org.assertj.core.api.Assertions.assertThat(
                        eventRepository
                                .findByProviderAndProviderEventId(
                                        "STRIPE", "evt_early_refund")
                                .orElseThrow()
                                .getOutcome())
                .isEqualTo("REVERSED");
    }

    @Test
    void providerBillingCountryMismatchNeverFulfils() throws Exception {
        String orderJson = mockMvc.perform(post("/api/v2/payments/orders")
                        .with(paymentGateway("owner-country"))
                        .header("Idempotency-Key", "country-checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutRequest("starter")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String orderId = objectMapper.readTree(orderJson).path("orderId").asText();
        mockMvc.perform(post("/api/v2/payments/orders/{id}/bind-stripe-session", orderId)
                        .with(stripeGateway("owner-country"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stripeSessionId\":\"cs_test_country\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v2/payments/provider-events/stripe")
                        .header(SERVICE_TOKEN, STRIPE_GATEWAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settledEvent(
                                "evt_country", orderId, "cs_test_country", "pi_country", "US")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("UNFULFILLED_MANUAL_REVIEW"))
                .andExpect(jsonPath("$.orderStatus").value("MANUAL_REVIEW"))
                .andExpect(jsonPath("$.grantedDocumentCredits").value(0));
        mockMvc.perform(get("/api/v2/payments/wallet").with(paymentGateway("owner-country")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceDocumentCredits").value(2));
    }

    @Test
    void accountDeletionRevokesAccessWithoutDeletingAuditEvidence() throws Exception {
        mockMvc.perform(get("/api/v2/payments/wallet").with(paymentGateway("owner-delete")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/internal/v2/payments/owners/{owner}/revoke-access", "owner-delete")
                        .with(identity(ACCOUNT_LIFECYCLE, "owner-delete")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCESS_REVOKED_RECORDS_RETAINED"))
                .andExpect(jsonPath("$.financialRecordRetentionYears").value(7))
                .andExpect(jsonPath("$.providerReconciliationEvidenceRetained").value(true));
        mockMvc.perform(post("/api/v2/payments/document-credit-reservations")
                        .with(documentGateway("owner-delete"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operationKey\":\"after-delete\",\"documentCredits\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PAYMENT_ACCESS_REVOKED"));
        mockMvc.perform(get("/api/v2/payments/transactions").with(paymentGateway("owner-delete")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions", hasSize(1)));
    }

    @Test
    void accountDeletionRetriesProviderExpiryAndRecoveryCannotStrandACheckout()
            throws Exception {
        String owner = "owner-delete-provider";
        String orderJson = mockMvc.perform(post("/api/v2/payments/orders")
                        .with(paymentGateway(owner))
                        .header("Idempotency-Key", "delete-provider-checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutRequest("starter")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String orderId = objectMapper.readTree(orderJson).path("orderId").asText();
        mockMvc.perform(post("/api/v2/payments/orders/{id}/bind-stripe-session", orderId)
                        .with(stripeGateway(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stripeSessionId\":\"cs_test_delete_provider\"}"))
                .andExpect(status().isOk());

        when(stripeLifecycleClient.expire(eq(owner), any())).thenReturn(false);
        mockMvc.perform(post("/internal/v2/payments/owners/{owner}/revoke-access", owner)
                        .with(identity(ACCOUNT_LIFECYCLE, owner)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PROVIDER_SESSION_EXPIRY_PENDING"));
        PaymentOrder providerPending = orderRepository.findById(
                java.util.UUID.fromString(orderId)).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(providerPending.getStatus().name())
                .isEqualTo("CHECKOUT_OPEN");
        org.assertj.core.api.Assertions.assertThat(providerPending.getManualReviewReason())
                .isNull();

        when(stripeLifecycleClient.expire(eq(owner), any())).thenReturn(true);
        providerSessionReconciliation.recoverPendingProviderSessions();

        mockMvc.perform(post("/internal/v2/payments/owners/{owner}/revoke-access", owner)
                        .with(identity(ACCOUNT_LIFECYCLE, owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCESS_REVOKED_RECORDS_RETAINED"))
                .andExpect(jsonPath("$.providerReconciliationEvidenceRetained").value(true))
                .andExpect(jsonPath("$.providerSessionsRequireExpiry").value(false))
                .andExpect(jsonPath("$.providerCheckoutSessionsToExpire", hasSize(0)));
        org.assertj.core.api.Assertions.assertThat(orderRepository.findById(
                        java.util.UUID.fromString(orderId)).orElseThrow().getManualReviewReason())
                .isEqualTo("ACCOUNT_ACCESS_REVOKED_PROVIDER_EXPIRED");

        mockMvc.perform(post("/api/v2/payments/orders")
                        .with(paymentGateway(owner))
                        .header("Idempotency-Key", "checkout-after-account-delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutRequest("starter")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PAYMENT_ACCESS_REVOKED"));
    }

    @Test
    void providerCompleteDuringDeletionRemainsBlockedUntilSignedSettlementIsRecorded()
            throws Exception {
        String owner = "owner-delete-provider-complete";
        String orderJson = mockMvc.perform(post("/api/v2/payments/orders")
                        .with(paymentGateway(owner))
                        .header("Idempotency-Key", "delete-complete-checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutRequest("starter")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String orderId = objectMapper.readTree(orderJson).path("orderId").asText();
        mockMvc.perform(post("/api/v2/payments/orders/{id}/bind-stripe-session", orderId)
                        .with(stripeGateway(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stripeSessionId\":\"cs_test_delete_complete\"}"))
                .andExpect(status().isOk());

        // A false result includes a provider-confirmed COMPLETE session. It is not expiry evidence.
        when(stripeLifecycleClient.expire(eq(owner), any())).thenReturn(false);
        mockMvc.perform(post("/internal/v2/payments/owners/{owner}/revoke-access", owner)
                        .with(identity(ACCOUNT_LIFECYCLE, owner)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PROVIDER_SESSION_EXPIRY_PENDING"));
        org.assertj.core.api.Assertions.assertThat(
                        walletRepository.findByUserId(owner).orElseThrow()
                                .getLifecycleStatus().name())
                .isEqualTo("REVOCATION_PENDING");

        String signedCompletion = settledEvent(
                "evt_delete_complete", orderId, "cs_test_delete_complete", "pi_delete", "GB")
                .replace("\"amountTotalMinor\":1699", "\"amountTotalMinor\":799");
        mockMvc.perform(post("/api/v2/payments/provider-events/stripe")
                        .header(SERVICE_TOKEN, STRIPE_GATEWAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signedCompletion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome")
                        .value("PAID_DURING_REVOCATION_REVIEW"))
                .andExpect(jsonPath("$.orderStatus").value("MANUAL_REVIEW"))
                .andExpect(jsonPath("$.grantedDocumentCredits").value(0));

        mockMvc.perform(post("/internal/v2/payments/owners/{owner}/revoke-access", owner)
                        .with(identity(ACCOUNT_LIFECYCLE, owner)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code")
                        .value("PAYMENT_FINANCIAL_RECONCILIATION_PENDING"));
        PaymentOrder reviewed = orderRepository.findById(
                java.util.UUID.fromString(orderId)).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(reviewed.getManualReviewReason())
                .isEqualTo("PAID_DURING_ACCOUNT_REVOCATION_REFUND_REQUIRED");
        org.assertj.core.api.Assertions.assertThat(
                        walletRepository.findByUserId(owner).orElseThrow().getBalanceCredits())
                .isEqualTo(2);
    }

    @Test
    void signedExpiryDuringDeletionClearsTheProviderBlockBeforeFinalRevocation()
            throws Exception {
        String owner = "owner-delete-signed-expiry";
        String orderJson = mockMvc.perform(post("/api/v2/payments/orders")
                        .with(paymentGateway(owner))
                        .header("Idempotency-Key", "delete-signed-expiry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutRequest("starter")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String orderId = objectMapper.readTree(orderJson).path("orderId").asText();
        mockMvc.perform(post("/api/v2/payments/orders/{id}/bind-stripe-session", orderId)
                        .with(stripeGateway(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stripeSessionId\":\"cs_test_delete_signed_expiry\"}"))
                .andExpect(status().isOk());
        when(stripeLifecycleClient.expire(eq(owner), any())).thenReturn(false);
        mockMvc.perform(post("/internal/v2/payments/owners/{owner}/revoke-access", owner)
                        .with(identity(ACCOUNT_LIFECYCLE, owner)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PROVIDER_SESSION_EXPIRY_PENDING"));

        mockMvc.perform(post("/api/v2/payments/provider-events/stripe")
                        .header(SERVICE_TOKEN, STRIPE_GATEWAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(expiredEvent(
                                "evt_delete_signed_expiry", orderId,
                                "cs_test_delete_signed_expiry")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ORDER_EXPIRED"));

        mockMvc.perform(post("/internal/v2/payments/owners/{owner}/revoke-access", owner)
                        .with(identity(ACCOUNT_LIFECYCLE, owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCESS_REVOKED_RECORDS_RETAINED"))
                .andExpect(jsonPath("$.providerSessionsRequireExpiry").value(false));
        org.assertj.core.api.Assertions.assertThat(
                        walletRepository.findByUserId(owner).orElseThrow()
                                .getLifecycleStatus().name())
                .isEqualTo("REVOKED");
    }

    @Test
    void completedPurchaseBeforeDeletionIsHistoricalAndDoesNotStrandNewCredits()
            throws Exception {
        String owner = "owner-paid-before-delete";
        String orderJson = mockMvc.perform(post("/api/v2/payments/orders")
                        .with(paymentGateway(owner))
                        .header("Idempotency-Key", "paid-before-delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutRequest("starter")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String orderId = objectMapper.readTree(orderJson).path("orderId").asText();
        mockMvc.perform(post("/api/v2/payments/orders/{id}/bind-stripe-session", orderId)
                        .with(stripeGateway(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stripeSessionId\":\"cs_test_paid_before_delete\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v2/payments/provider-events/stripe")
                        .header(SERVICE_TOKEN, STRIPE_GATEWAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settledEvent(
                                "evt_paid_before_delete", orderId,
                                "cs_test_paid_before_delete", "pi_paid_before_delete", "GB")
                                .replace("\"amountTotalMinor\":1699", "\"amountTotalMinor\":799")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("FULFILLED"));

        mockMvc.perform(post("/internal/v2/payments/owners/{owner}/revoke-access", owner)
                        .with(identity(ACCOUNT_LIFECYCLE, owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCESS_REVOKED_RECORDS_RETAINED"));
        org.assertj.core.api.Assertions.assertThat(
                        orderRepository.findById(java.util.UUID.fromString(orderId))
                                .orElseThrow().getStatus().name())
                .isEqualTo("FULFILLED");
        org.assertj.core.api.Assertions.assertThat(
                        walletRepository.findByUserId(owner).orElseThrow()
                                .getLifecycleStatus().name())
                .isEqualTo("REVOKED");
    }

    @Test
    void checkoutRejectsMissingConsumerAcknowledgementsAndExportsDurableEvidence() throws Exception {
        mockMvc.perform(post("/api/v2/payments/orders")
                        .with(paymentGateway("owner-terms"))
                        .header("Idempotency-Key", "checkout-without-consent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pricingPlanId\":\"starter\",\"billingCountry\":\"GB\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CONSUMER_ACKNOWLEDGEMENTS_REQUIRED"));

        mockMvc.perform(post("/api/v2/payments/orders")
                        .with(paymentGateway("owner-terms"))
                        .header("Idempotency-Key", "checkout-with-consent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutRequest("starter")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/internal/v2/payments/owners/{owner}/export", "owner-terms")
                        .with(identity(ACCOUNT_LIFECYCLE, "owner-terms")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value("payment-export-v1"))
                .andExpect(jsonPath("$.orders", hasSize(1)))
                .andExpect(jsonPath("$.orders[0].consumerTermsVersion")
                        .value("uk-consumer-terms-2026-08-15"))
                .andExpect(jsonPath("$.orders[0].taxStatus")
                        .value("NOT_VAT_REGISTERED"))
                .andExpect(jsonPath("$.orders[0].legalEntityType")
                        .value("SOLE_TRADER"))
                .andExpect(jsonPath("$.orders[0].consumerAcknowledgementsRecorded").value(true));
    }

    @Test
    void recoveryReleasesExpiredCreditAndPromotionReservationsExactlyOnce() throws Exception {
        mockMvc.perform(get("/api/v2/payments/wallet").with(paymentGateway("owner-recovery")))
                .andExpect(status().isOk());
        String reservationJson = mockMvc.perform(post("/api/v2/payments/document-credit-reservations")
                        .with(documentGateway("owner-recovery"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operationKey\":\"crash-before-delivery\",\"documentCredits\":1}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        DocumentCreditReservation reservation = reservationRepository.findById(
                java.util.UUID.fromString(objectMapper.readTree(reservationJson)
                        .path("reservationId").asText())).orElseThrow();
        Instant reconciliationTime = Instant.now().plusSeconds(60);
        reservation.setExpiresAt(Instant.now().plusSeconds(1));
        reservationRepository.saveAndFlush(reservation);

        String orderJson = mockMvc.perform(post("/api/v2/payments/orders")
                        .with(paymentGateway("owner-recovery"))
                        .header("Idempotency-Key", "recovery-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutRequest("starter")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        PaymentOrder order = orderRepository.findById(java.util.UUID.fromString(
                objectMapper.readTree(orderJson).path("orderId").asText())).orElseThrow();
        order.setExpiresAt(Instant.now().plusSeconds(1));
        orderRepository.saveAndFlush(order);

        CommercialReservationRecoveryService.RecoveryResult first =
                commercialRecoveryService.reconcileExpiredCommercialReservations(reconciliationTime);
        CommercialReservationRecoveryService.RecoveryResult replay =
                commercialRecoveryService.reconcileExpiredCommercialReservations(reconciliationTime);

        org.assertj.core.api.Assertions.assertThat(first.releasedDocumentReservations()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(first.expiredPaymentOrders()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(replay.releasedDocumentReservations()).isZero();
        org.assertj.core.api.Assertions.assertThat(replay.expiredPaymentOrders()).isZero();
        org.assertj.core.api.Assertions.assertThat(
                walletRepository.findByUserId("owner-recovery").orElseThrow().getBalanceCredits())
                .isEqualTo(2);
        PaymentOrder expiredOrder = orderRepository.findById(order.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(expiredOrder.getStatus().name())
                .isEqualTo("EXPIRED");
        org.assertj.core.api.Assertions.assertThat(expiredOrder.getPromotionBonusCredits())
                .as("the expired order keeps the immutable promised bonus snapshot")
                .isEqualTo(5);
        org.assertj.core.api.Assertions.assertThat(
                promotionReservationRepository.findByOrderId(order.getId()).orElseThrow()
                        .getStatus().name())
                .isEqualTo("RELEASED");
        org.assertj.core.api.Assertions.assertThat(
                campaignRepository.findById("founding-200").orElseThrow().getActiveReservations())
                .isZero();
    }

    @Test
    void boundCheckoutIsNeverClockExpiredAndSignedCompletionWinsBeforeProviderExpiry()
            throws Exception {
        String owner = "owner-bound-expiry-ordering";
        String orderJson = mockMvc.perform(post("/api/v2/payments/orders")
                        .with(paymentGateway(owner))
                        .header("Idempotency-Key", "bound-expiry-ordering")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutRequest("starter")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String orderId = objectMapper.readTree(orderJson).path("orderId").asText();
        mockMvc.perform(post("/api/v2/payments/orders/{id}/bind-stripe-session", orderId)
                        .with(stripeGateway(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stripeSessionId\":\"cs_test_expiry_ordering\"}"))
                .andExpect(status().isOk());
        PaymentOrder elapsed = orderRepository.findById(
                java.util.UUID.fromString(orderId)).orElseThrow();
        elapsed.setExpiresAt(Instant.now().minusSeconds(1));
        orderRepository.saveAndFlush(elapsed);

        CommercialReservationRecoveryService.RecoveryResult localRecovery =
                commercialRecoveryService.reconcileExpiredCommercialReservations(Instant.now());
        org.assertj.core.api.Assertions.assertThat(localRecovery.expiredPaymentOrders()).isZero();
        org.assertj.core.api.Assertions.assertThat(orderRepository.findById(elapsed.getId())
                        .orElseThrow().getStatus().name())
                .isEqualTo("CHECKOUT_OPEN");

        // A provider that reports non-expired (including COMPLETE) leaves the order open.
        when(stripeLifecycleClient.expire(eq(owner), any())).thenReturn(false);
        providerSessionReconciliation.recoverPendingProviderSessions();
        org.assertj.core.api.Assertions.assertThat(orderRepository.findById(elapsed.getId())
                        .orElseThrow().getStatus().name())
                .isEqualTo("CHECKOUT_OPEN");

        mockMvc.perform(post("/api/v2/payments/provider-events/stripe")
                        .header(SERVICE_TOKEN, STRIPE_GATEWAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settledEvent(
                                "evt_expiry_ordering_paid", orderId,
                                "cs_test_expiry_ordering", "pi_expiry_ordering", "GB")
                                .replace("\"amountTotalMinor\":1699", "\"amountTotalMinor\":799")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("FULFILLED"))
                .andExpect(jsonPath("$.grantedDocumentCredits").value(15));

        // A delayed, valid expiry webhook cannot regress a durably fulfilled order.
        mockMvc.perform(post("/api/v2/payments/provider-events/stripe")
                        .header(SERVICE_TOKEN, STRIPE_GATEWAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(expiredEvent(
                                "evt_expiry_ordering_late", orderId,
                                "cs_test_expiry_ordering")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("TERMINAL_ORDER_UNCHANGED"))
                .andExpect(jsonPath("$.orderStatus").value("FULFILLED"));
    }

    @Test
    void providerConfirmedOrSignedExpiryReleasesBoundCheckoutExactlyOnce()
            throws Exception {
        String providerOwner = "owner-provider-expiry";
        String providerOrderJson = mockMvc.perform(post("/api/v2/payments/orders")
                        .with(paymentGateway(providerOwner))
                        .header("Idempotency-Key", "provider-expiry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutRequest("starter")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String providerOrderId = objectMapper.readTree(providerOrderJson).path("orderId").asText();
        mockMvc.perform(post("/api/v2/payments/orders/{id}/bind-stripe-session", providerOrderId)
                        .with(stripeGateway(providerOwner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stripeSessionId\":\"cs_test_provider_expired\"}"))
                .andExpect(status().isOk());
        PaymentOrder elapsed = orderRepository.findById(
                java.util.UUID.fromString(providerOrderId)).orElseThrow();
        elapsed.setExpiresAt(Instant.now().minusSeconds(1));
        orderRepository.saveAndFlush(elapsed);
        when(stripeLifecycleClient.expire(eq(providerOwner), any())).thenReturn(true);
        providerSessionReconciliation.recoverPendingProviderSessions();
        providerSessionReconciliation.recoverPendingProviderSessions();
        org.assertj.core.api.Assertions.assertThat(orderRepository.findById(elapsed.getId())
                        .orElseThrow().getStatus().name())
                .isEqualTo("EXPIRED");

        String signedOwner = "owner-signed-expiry";
        String signedOrderJson = mockMvc.perform(post("/api/v2/payments/orders")
                        .with(paymentGateway(signedOwner))
                        .header("Idempotency-Key", "signed-expiry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutRequest("starter")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String signedOrderId = objectMapper.readTree(signedOrderJson).path("orderId").asText();
        mockMvc.perform(post("/api/v2/payments/orders/{id}/bind-stripe-session", signedOrderId)
                        .with(stripeGateway(signedOwner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stripeSessionId\":\"cs_test_signed_expired\"}"))
                .andExpect(status().isOk());
        String signedExpiry = expiredEvent(
                "evt_signed_expired", signedOrderId, "cs_test_signed_expired");
        mockMvc.perform(post("/api/v2/payments/provider-events/stripe")
                        .header(SERVICE_TOKEN, STRIPE_GATEWAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signedExpiry))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ORDER_EXPIRED"));
        mockMvc.perform(post("/api/v2/payments/provider-events/stripe")
                        .header(SERVICE_TOKEN, STRIPE_GATEWAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signedExpiry))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ORDER_EXPIRED"))
                .andExpect(jsonPath("$.grantedDocumentCredits").value(0));
    }

    @Test
    void reversalAggregateNeverRegressesAndDisputeHasPrecedence() throws Exception {
        String disputedOrder = createAndFulfilOrder(
                "owner-dispute-ordering", "active", "dispute-ordering",
                "cs_test_dispute_ordering", "pi_dispute_ordering");
        mockMvc.perform(post("/api/v2/payments/provider-events/stripe")
                        .header(SERVICE_TOKEN, STRIPE_GATEWAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reversalEvent(
                                "evt_partial_before_dispute", "charge.refunded",
                                disputedOrder, "pi_dispute_ordering", 800)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("PARTIALLY_REFUNDED"));
        mockMvc.perform(post("/api/v2/payments/provider-events/stripe")
                        .header(SERVICE_TOKEN, STRIPE_GATEWAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reversalEvent(
                                "evt_dispute_after_partial", "charge.dispute.created",
                                disputedOrder, "pi_dispute_ordering", 1699)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("DISPUTED"));
        mockMvc.perform(post("/api/v2/payments/provider-events/stripe")
                        .header(SERVICE_TOKEN, STRIPE_GATEWAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reversalEvent(
                                "evt_refund_after_dispute", "charge.refunded",
                                disputedOrder, "pi_dispute_ordering", 1699)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("DISPUTED"));

        String refundedOrder = createAndFulfilOrder(
                "owner-refund-ordering", "starter", "refund-ordering",
                "cs_test_refund_ordering", "pi_refund_ordering");
        mockMvc.perform(post("/api/v2/payments/provider-events/stripe")
                        .header(SERVICE_TOKEN, STRIPE_GATEWAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reversalEvent(
                                "evt_full_refund_first", "charge.refunded",
                                refundedOrder, "pi_refund_ordering", 799)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("REFUNDED"));
        mockMvc.perform(post("/api/v2/payments/provider-events/stripe")
                        .header(SERVICE_TOKEN, STRIPE_GATEWAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reversalEvent(
                                "evt_stale_partial_refund", "charge.refunded",
                                refundedOrder, "pi_refund_ordering", 300)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("REFUNDED"));
    }

    @Test
    void concurrentDuplicateProviderDeliveryReplaysWithoutDoubleGrantOrServerError() throws Exception {
        String orderJson = mockMvc.perform(post("/api/v2/payments/orders")
                        .with(paymentGateway("owner-concurrent-event"))
                        .header("Idempotency-Key", "concurrent-event-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutRequest("starter")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String orderId = objectMapper.readTree(orderJson).path("orderId").asText();
        mockMvc.perform(post("/api/v2/payments/orders/{id}/bind-stripe-session", orderId)
                        .with(stripeGateway("owner-concurrent-event"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stripeSessionId\":\"cs_test_concurrent\"}"))
                .andExpect(status().isOk());
        String event = settledEvent(
                "evt_concurrent_same", orderId, "cs_test_concurrent", "pi_concurrent", "GB")
                .replace("\"amountTotalMinor\":1699", "\"amountTotalMinor\":799");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<org.springframework.test.web.servlet.MvcResult> first =
                    CompletableFuture.supplyAsync(() -> postProviderEvent(event), executor);
            CompletableFuture<org.springframework.test.web.servlet.MvcResult> second =
                    CompletableFuture.supplyAsync(() -> postProviderEvent(event), executor);
            List<org.springframework.test.web.servlet.MvcResult> results =
                    List.of(first.join(), second.join());
            org.assertj.core.api.Assertions.assertThat(results)
                    .allMatch(result -> result.getResponse().getStatus() == 200);
            int totalGranted = results.stream().mapToInt(result -> {
                try {
                    return objectMapper.readTree(result.getResponse().getContentAsString())
                            .path("grantedDocumentCredits").asInt();
                } catch (Exception failure) {
                    throw new RuntimeException(failure);
                }
            }).sum();
            org.assertj.core.api.Assertions.assertThat(totalGranted).isEqualTo(15);
        } finally {
            executor.shutdownNow();
        }
        org.assertj.core.api.Assertions.assertThat(
                walletRepository.findByUserId("owner-concurrent-event").orElseThrow()
                        .getBalanceCredits()).isEqualTo(17);
        org.assertj.core.api.Assertions.assertThat(eventRepository.count()).isEqualTo(1);
    }

    @Test
    void concurrentUnmatchedProviderReplayIsSerializedBeforeTheUniqueConstraint()
            throws Exception {
        String event = objectMapper.writeValueAsString(
                objectMapper.createObjectNode()
                        .put("providerEventId", "evt_concurrent_unmatched")
                        .put("eventType", "charge.refunded")
                        .put("payloadSha256", "d".repeat(64))
                        .put("paymentIntentId", "pi_not_yet_linked")
                        .put("currency", "GBP")
                        .put("liveMode", false)
                        .put("reversalAmountMinor", 799));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<org.springframework.test.web.servlet.MvcResult> first =
                    CompletableFuture.supplyAsync(() -> postProviderEvent(event), executor);
            CompletableFuture<org.springframework.test.web.servlet.MvcResult> second =
                    CompletableFuture.supplyAsync(() -> postProviderEvent(event), executor);
            List<org.springframework.test.web.servlet.MvcResult> results =
                    List.of(first.join(), second.join());
            org.assertj.core.api.Assertions.assertThat(results)
                    .allMatch(result -> result.getResponse().getStatus() == 200)
                    .allMatch(result -> {
                        try {
                            return "UNMATCHED_REVERSAL_MANUAL_REVIEW".equals(
                                    objectMapper.readTree(result.getResponse().getContentAsString())
                                            .path("outcome").asText());
                        } catch (Exception failure) {
                            throw new RuntimeException(failure);
                        }
                    });
        } finally {
            executor.shutdownNow();
        }
        org.assertj.core.api.Assertions.assertThat(eventRepository.count()).isEqualTo(1);
    }

    private org.springframework.test.web.servlet.MvcResult postProviderEvent(String content) {
        try {
            return mockMvc.perform(post("/api/v2/payments/provider-events/stripe")
                            .header(SERVICE_TOKEN, STRIPE_GATEWAY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(content))
                    .andReturn();
        } catch (Exception failure) {
            throw new RuntimeException(failure);
        }
    }

    private String createAndFulfilOrder(
            String owner,
            String plan,
            String idempotencyKey,
            String sessionId,
            String paymentIntentId) throws Exception {
        String orderJson = mockMvc.perform(post("/api/v2/payments/orders")
                        .with(paymentGateway(owner))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutRequest(plan)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String orderId = objectMapper.readTree(orderJson).path("orderId").asText();
        mockMvc.perform(post("/api/v2/payments/orders/{id}/bind-stripe-session", orderId)
                        .with(stripeGateway(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stripeSessionId\":\"" + sessionId + "\"}"))
                .andExpect(status().isOk());
        String completion = settledEvent(
                "evt_complete_" + idempotencyKey,
                orderId,
                sessionId,
                paymentIntentId,
                "GB");
        if ("starter".equals(plan)) {
            completion = completion.replace(
                    "\"amountTotalMinor\":1699", "\"amountTotalMinor\":799");
        }
        mockMvc.perform(post("/api/v2/payments/provider-events/stripe")
                        .header(SERVICE_TOKEN, STRIPE_GATEWAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("FULFILLED"));
        return orderId;
    }

    private String expiredEvent(String eventId, String orderId, String sessionId)
            throws Exception {
        return objectMapper.writeValueAsString(objectMapper.createObjectNode()
                .put("providerEventId", eventId)
                .put("eventType", "checkout.session.expired")
                .put("payloadSha256", "e".repeat(64))
                .put("orderId", orderId)
                .put("stripeSessionId", sessionId)
                .put("liveMode", false)
                .put("eventCreatedAt", Instant.now().toString()));
    }

    private String reversalEvent(
            String eventId,
            String eventType,
            String orderId,
            String paymentIntentId,
            long amountMinor) throws Exception {
        return objectMapper.writeValueAsString(objectMapper.createObjectNode()
                .put("providerEventId", eventId)
                .put("eventType", eventType)
                .put("payloadSha256", "f".repeat(64))
                .put("orderId", orderId)
                .put("paymentIntentId", paymentIntentId)
                .put("currency", "GBP")
                .put("liveMode", false)
                .put("reversalAmountMinor", amountMinor)
                .put("eventCreatedAt", Instant.now().toString()));
    }

    private String checkoutRequest(String plan) {
        return "{\"pricingPlanId\":\"" + plan + "\",\"billingCountry\":\"GB\","
                + "\"immediateSupplyRequested\":true,"
                + "\"cancellationRightLossAcknowledged\":true}";
    }

    private String settledEvent(
            String eventId, String orderId, String sessionId, String paymentIntent, String country)
            throws Exception {
        return objectMapper.writeValueAsString(objectMapper.createObjectNode()
                .put("providerEventId", eventId)
                .put("eventType", "checkout.session.completed")
                .put("payloadSha256", "a".repeat(64))
                .put("orderId", orderId)
                .put("stripeSessionId", sessionId)
                .put("paymentIntentId", paymentIntent)
                .put("paymentStatus", "paid")
                .put("checkoutStatus", "complete")
                .put("currency", "GBP")
                .put("amountTotalMinor", sessionId.contains("country") ? 799 : 1699)
                .put("billingCountry", country)
                .put("liveMode", false)
                .put("eventCreatedAt", Instant.now().toString()));
    }

    private String refundEvent(String orderId) throws Exception {
        return objectMapper.writeValueAsString(objectMapper.createObjectNode()
                .put("providerEventId", "evt_refund_1")
                .put("eventType", "charge.refunded")
                .put("payloadSha256", "b".repeat(64))
                .put("orderId", orderId)
                .put("paymentIntentId", "pi_test_owned")
                .put("liveMode", false)
                .put("reversalAmountMinor", 1699));
    }

    private RequestPostProcessor paymentGateway(String owner) {
        return identity(PAYMENT_GATEWAY, owner);
    }

    private RequestPostProcessor documentGateway(String owner) {
        return identity(DOCUMENT_GATEWAY, owner);
    }

    private RequestPostProcessor stripeGateway(String owner) {
        return identity(STRIPE_GATEWAY, owner);
    }

    private RequestPostProcessor identity(String token, String owner) {
        return request -> {
            request.addHeader(SERVICE_TOKEN, token);
            request.addHeader(OWNER, owner);
            return request;
        };
    }
}
