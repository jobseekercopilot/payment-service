package com.jobseekercopilot.paymentservice.systemdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseekercopilot.paymentservice.entity.AiTokenTransaction;
import com.jobseekercopilot.paymentservice.entity.AiTokenWallet;
import com.jobseekercopilot.paymentservice.entity.TransactionType;
import com.jobseekercopilot.paymentservice.repository.AiTokenReservationRepository;
import com.jobseekercopilot.paymentservice.repository.AiTokenTransactionRepository;
import com.jobseekercopilot.paymentservice.repository.AiTokenWalletRepository;
import com.jobseekercopilot.paymentservice.repository.DocumentCreditReservationRepository;
import com.jobseekercopilot.paymentservice.repository.DocumentCreditTransactionRepository;
import com.jobseekercopilot.paymentservice.repository.DocumentCreditWalletRepository;
import com.jobseekercopilot.paymentservice.repository.FoundingPromotionReservationRepository;
import com.jobseekercopilot.paymentservice.repository.FoundingPromotionCampaignRepository;
import com.jobseekercopilot.paymentservice.repository.PaymentOrderRepository;
import com.jobseekercopilot.paymentservice.repository.PaymentProviderEventRepository;
import com.jobseekercopilot.paymentservice.service.LedgerReconciliationService;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "environment-data.enabled=true",
    "environment-data.isolated-database=true",
    "environment-data.allowed-environments=test",
    "environment-data.token=environment-data-integration-token-00000001",
    "payment.checkout.enabled=true",
    "payment.checkout.release-authorised=true",
    "payment.tax-status=NOT_VAT_REGISTERED",
    "payment.legal-entity.type=SOLE_TRADER",
    "payment.legal-entity.configuration-version=test-reviewed-v1",
    "payment.legal-entity.reviewed=true",
    "payment.promotion.enabled=true",
    "payment.promotion.release-authorised=true"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PaymentSystemDataLedgerIntegrationTest {
    private static final String ENVIRONMENT_DATA_TOKEN =
            "environment-data-integration-token-00000001";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AiTokenWalletRepository walletRepository;
    @Autowired private AiTokenTransactionRepository transactionRepository;
    @Autowired private AiTokenReservationRepository reservationRepository;
    @Autowired private DocumentCreditWalletRepository documentCreditWalletRepository;
    @Autowired private DocumentCreditTransactionRepository documentCreditTransactionRepository;
    @Autowired private DocumentCreditReservationRepository documentCreditReservationRepository;
    @Autowired private PaymentOrderRepository paymentOrderRepository;
    @Autowired private PaymentProviderEventRepository paymentProviderEventRepository;
    @Autowired private FoundingPromotionReservationRepository promotionReservationRepository;
    @Autowired private FoundingPromotionCampaignRepository campaignRepository;
    @Autowired private LedgerReconciliationService reconciliationService;

    @BeforeEach
    void clean() {
        paymentProviderEventRepository.deleteAll();
        promotionReservationRepository.deleteAll();
        paymentOrderRepository.deleteAll();
        documentCreditReservationRepository.deleteAll();
        documentCreditTransactionRepository.deleteAll();
        documentCreditWalletRepository.deleteAll();
        var campaign = campaignRepository.findById("founding-200").orElseThrow();
        campaign.setEnabled(true);
        campaign.setActiveReservations(0);
        campaign.setCompletedClaims(0);
        campaignRepository.saveAndFlush(campaign);
        reservationRepository.deleteAll();
        transactionRepository.deleteAll();
        walletRepository.deleteAll();
    }

    @Test
    void acceptsAndReconcilesACompleteIsolatedLedgerFixture() throws Exception {
        UUID walletId = UUID.randomUUID();
        AiTokenWallet wallet = wallet(walletId, 12_000);
        SystemDataPaymentSeedRequest request = new SystemDataPaymentSeedRequest(
                "scenario-v1",
                "fixture-owner",
                wallet,
                List.of(
                        entry(
                                walletId,
                                TransactionType.DEMO_PURCHASE,
                                20_000,
                                0,
                                20_000,
                                "FIXTURE:scenario-v1:purchase",
                                Instant.parse("2026-01-01T00:00:00Z")),
                        entry(
                                walletId,
                                TransactionType.SPEND,
                                -8_000,
                                20_000,
                                12_000,
                                "FIXTURE:scenario-v1:spend",
                                Instant.parse("2026-01-02T00:00:00Z"))),
                List.of());

        mockMvc.perform(post("/internal/system-data/seed/payments")
                        .header(EnvironmentDataGuard.TOKEN_HEADER, ENVIRONMENT_DATA_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordsAffected").value(3))
                .andExpect(jsonPath("$.details.balanceTokens").value(12_000))
                .andExpect(jsonPath("$.details.ledgerEntries").value(2));

        AiTokenWallet savedWallet =
                walletRepository.findByUserId("fixture-owner").orElseThrow();
        assertThat(reconciliationService.reconcile(savedWallet.getId()).reconciled()).isTrue();
        assertThat(transactionRepository.findByWalletIdOrderBySequenceNumberAsc(savedWallet.getId()))
                .extracting(AiTokenTransaction::getSequenceNumber)
                .isSorted()
                .doesNotHaveDuplicates()
                .allSatisfy(sequence -> assertThat(sequence).isPositive());
    }

    @Test
    void rejectsAndRollsBackADivergentFixture() throws Exception {
        UUID walletId = UUID.randomUUID();
        SystemDataPaymentSeedRequest request = new SystemDataPaymentSeedRequest(
                "scenario-v1",
                "fixture-owner",
                wallet(walletId, 12_001),
                List.of(entry(
                        walletId,
                        TransactionType.ADJUSTMENT,
                        12_000,
                        0,
                        12_000,
                        "FIXTURE:scenario-v1:invalid",
                        Instant.parse("2026-01-01T00:00:00Z"))),
                List.of());

        mockMvc.perform(post("/internal/system-data/seed/payments")
                        .header(EnvironmentDataGuard.TOKEN_HEADER, ENVIRONMENT_DATA_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Invalid isolated payment fixture: Fixture wallet balance does not reconcile with its ledger"));

        assertThat(walletRepository.findById(walletId)).isEmpty();
        assertThat(transactionRepository.findByWalletIdOrderBySequenceNumberAsc(walletId))
                .isEmpty();
    }

    @Test
    void runtimeOwnerCleanupIsSyntheticBoundedCrossOwnerSafeAndRepeatable()
            throws Exception {
        String scenarioId = "cross-user-security-v1";
        String identityKey = "claimant-a";
        UUID ownerId = syntheticOwner(scenarioId, identityKey);
        UUID walletId = UUID.randomUUID();
        AiTokenWallet ownerWallet = wallet(
                walletId, ownerId.toString(), 12_000);
        SystemDataPaymentSeedRequest ownerRequest =
                new SystemDataPaymentSeedRequest(
                        scenarioId,
                        ownerId.toString(),
                        ownerWallet,
                        List.of(
                                entry(
                                        walletId,
                                        ownerId.toString(),
                                        TransactionType.DEMO_PURCHASE,
                                        20_000,
                                        0,
                                        20_000,
                                        "FIXTURE:cross-user-security-v1:purchase",
                                        Instant.parse("2026-01-01T00:00:00Z")),
                                entry(
                                        walletId,
                                        ownerId.toString(),
                                        TransactionType.SPEND,
                                        -8_000,
                                        20_000,
                                        12_000,
                                        "FIXTURE:cross-user-security-v1:spend",
                                        Instant.parse("2026-01-02T00:00:00Z"))),
                        List.of());
        mockMvc.perform(post("/internal/system-data/seed/payments")
                        .header(
                                EnvironmentDataGuard.TOKEN_HEADER,
                                ENVIRONMENT_DATA_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(ownerRequest)))
                .andExpect(status().isOk());
        walletRepository.saveAndFlush(wallet(
                UUID.randomUUID(),
                syntheticOwner(scenarioId, "claimant-b").toString(),
                5_000));

        String path = "/internal/system-data/v1/runtime-owners/{scenarioId}"
                + "/identities/{identityKey}/owners/{userId}";
        mockMvc.perform(get(path, scenarioId, identityKey, ownerId)
                        .header(
                                EnvironmentDataGuard.TOKEN_HEADER,
                                ENVIRONMENT_DATA_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.details.wallets").value(1))
                .andExpect(jsonPath("$.details.ledgerEntries").value(2));

        mockMvc.perform(delete(
                                path,
                                scenarioId,
                                identityKey,
                                UUID.randomUUID())
                        .header(
                                EnvironmentDataGuard.TOKEN_HEADER,
                                ENVIRONMENT_DATA_TOKEN))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete(path, scenarioId, identityKey, ownerId)
                        .header(
                                EnvironmentDataGuard.TOKEN_HEADER,
                                ENVIRONMENT_DATA_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordsAffected").value(3));
        assertThat(walletRepository.findByUserId(ownerId.toString())).isEmpty();
        assertThat(walletRepository.findAll()).hasSize(1);

        mockMvc.perform(delete(path, scenarioId, identityKey, ownerId)
                        .header(
                                EnvironmentDataGuard.TOKEN_HEADER,
                                ENVIRONMENT_DATA_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordsAffected").value(0));
    }

    @Test
    void documentCreditRuntimeWalletSeedResetAndReseedIsExactAndIdempotent()
            throws Exception {
        String scenarioId = "payment-acceptance-v1";
        String identityKey = "payment-primary";
        UUID ownerId = syntheticOwner(scenarioId, identityKey);
        String seedPath = "/internal/system-data/v2/runtime-owners/{scenarioId}"
                + "/identities/{identityKey}/owners/{userId}/document-credit-wallet";
        String ownerPath = "/internal/system-data/v1/runtime-owners/{scenarioId}"
                + "/identities/{identityKey}/owners/{userId}";

        mockMvc.perform(post(seedPath, scenarioId, identityKey, ownerId)
                        .header(EnvironmentDataGuard.TOKEN_HEADER, ENVIRONMENT_DATA_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation")
                        .value("SEED_RUNTIME_DOCUMENT_CREDIT_WALLET"))
                .andExpect(jsonPath("$.details.documentCreditWallets").value(1))
                .andExpect(jsonPath("$.details.balanceDocumentCredits").value(2))
                .andExpect(jsonPath("$.details.ledgerEntries").value(1));

        mockMvc.perform(post(seedPath, scenarioId, identityKey, ownerId)
                        .header(EnvironmentDataGuard.TOKEN_HEADER, ENVIRONMENT_DATA_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.details.documentCreditWallets").value(1))
                .andExpect(jsonPath("$.details.balanceDocumentCredits").value(2))
                .andExpect(jsonPath("$.details.ledgerEntries").value(1));

        mockMvc.perform(delete(ownerPath, scenarioId, identityKey, ownerId)
                        .header(EnvironmentDataGuard.TOKEN_HEADER, ENVIRONMENT_DATA_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordsAffected").value(2))
                .andExpect(jsonPath("$.details.documentCreditWallets").value(1))
                .andExpect(jsonPath("$.details.balanceDocumentCredits").value(2));
        assertThat(documentCreditWalletRepository.findByUserId(ownerId.toString())).isEmpty();
        assertThat(documentCreditTransactionRepository
                .findByUserIdOrderBySequenceNumberAsc(ownerId.toString())).isEmpty();

        mockMvc.perform(delete(ownerPath, scenarioId, identityKey, ownerId)
                        .header(EnvironmentDataGuard.TOKEN_HEADER, ENVIRONMENT_DATA_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordsAffected").value(0));

        mockMvc.perform(post(seedPath, scenarioId, identityKey, ownerId)
                        .header(EnvironmentDataGuard.TOKEN_HEADER, ENVIRONMENT_DATA_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.details.documentCreditWallets").value(1))
                .andExpect(jsonPath("$.details.balanceDocumentCredits").value(2))
                .andExpect(jsonPath("$.details.ledgerEntries").value(1));
    }

    @Test
    void documentCreditResetRemovesTheWholeOwnedAggregateAndPreservesNeighbour()
            throws Exception {
        String scenarioId = "payment-acceptance-v1";
        String identityKey = "settled-owner";
        String neighbourKey = "neighbour-owner";
        UUID ownerId = syntheticOwner(scenarioId, identityKey);
        UUID neighbourId = syntheticOwner(scenarioId, neighbourKey);
        seedDocumentWallet(scenarioId, identityKey, ownerId);
        seedDocumentWallet(scenarioId, neighbourKey, neighbourId);

        String ownerOrder = createOrder(ownerId, "owner-paid", "starter");
        bindOrder(ownerId, ownerOrder, "cs_test_reset_owner");
        mockMvc.perform(post("/api/v2/payments/provider-events/stripe")
                        .header("X-Service-Token", "stripe-gateway-test-token-0000000000000001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"providerEventId":"evt_reset_owner_paid",
                                 "eventType":"checkout.session.completed",
                                 "payloadSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                                 "orderId":"%s","stripeSessionId":"cs_test_reset_owner",
                                 "paymentIntentId":"pi_test_reset_owner","paymentStatus":"paid",
                                 "checkoutStatus":"complete","currency":"gbp",
                                 "amountTotalMinor":799,"billingCountry":"GB",
                                 "liveMode":false,"eventCreatedAt":"2026-08-15T09:00:00Z"}
                                """.formatted(ownerOrder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("FULFILLED"));
        createOrder(ownerId, "owner-pending", "active");
        createOrder(neighbourId, "neighbour-pending", "power");
        mockMvc.perform(post("/api/v2/payments/document-credit-reservations")
                        .header("X-Service-Token",
                                "document-generation-gateway-test-token-00000001")
                        .header("X-Payment-Owner", ownerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operationKey":"reset-owner:reserved-cv",
                                 "documentCredits":1,"regeneration":false,
                                 "referenceType":"GENERATION_OUTPUT","referenceId":"reset-owner:CV"}
                                """))
                .andExpect(status().isOk());

        var campaign = campaignRepository.findById("founding-200").orElseThrow();
        assertThat(campaign.getCompletedClaims()).isEqualTo(1);
        assertThat(campaign.getActiveReservations()).isEqualTo(1);
        assertThat(paymentOrderRepository.findByUserIdOrderByCreatedAtAsc(ownerId.toString()))
                .hasSize(2);
        assertThat(paymentProviderEventRepository.findAll()).hasSize(1);
        assertThat(documentCreditReservationRepository
                .findByUserIdOrderByCreatedAtAsc(ownerId.toString())).hasSize(1);
        assertThat(documentCreditTransactionRepository
                .findByUserIdOrderBySequenceNumberAsc(ownerId.toString())).hasSize(4);

        String ownerPath = "/internal/system-data/v1/runtime-owners/{scenarioId}"
                + "/identities/{identityKey}/owners/{userId}";
        mockMvc.perform(delete(ownerPath, scenarioId, identityKey, ownerId)
                        .header(EnvironmentDataGuard.TOKEN_HEADER, ENVIRONMENT_DATA_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.details.orders").value(2))
                .andExpect(jsonPath("$.details.providerEvents").value(1))
                .andExpect(jsonPath("$.details.promotionReservations").value(1))
                .andExpect(jsonPath("$.details.reservations").value(1));

        assertThat(documentCreditWalletRepository.findByUserId(ownerId.toString())).isEmpty();
        assertThat(documentCreditTransactionRepository
                .findByUserIdOrderBySequenceNumberAsc(ownerId.toString())).isEmpty();
        assertThat(documentCreditReservationRepository
                .findByUserIdOrderByCreatedAtAsc(ownerId.toString())).isEmpty();
        assertThat(paymentOrderRepository.findByUserIdOrderByCreatedAtAsc(ownerId.toString()))
                .isEmpty();
        assertThat(paymentProviderEventRepository.findAll()).isEmpty();
        assertThat(documentCreditWalletRepository.findByUserId(neighbourId.toString())).isPresent();
        assertThat(paymentOrderRepository.findByUserIdOrderByCreatedAtAsc(neighbourId.toString()))
                .hasSize(1);
        campaign = campaignRepository.findById("founding-200").orElseThrow();
        assertThat(campaign.getCompletedClaims()).isZero();
        assertThat(campaign.getActiveReservations()).isEqualTo(1);

        mockMvc.perform(delete(ownerPath, scenarioId, identityKey, ownerId)
                        .header(EnvironmentDataGuard.TOKEN_HEADER, ENVIRONMENT_DATA_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordsAffected").value(0));
    }

    private void seedDocumentWallet(String scenarioId, String identityKey, UUID ownerId)
            throws Exception {
        mockMvc.perform(post("/internal/system-data/v2/runtime-owners/{scenarioId}"
                                + "/identities/{identityKey}/owners/{userId}/document-credit-wallet",
                        scenarioId, identityKey, ownerId)
                        .header(EnvironmentDataGuard.TOKEN_HEADER, ENVIRONMENT_DATA_TOKEN))
                .andExpect(status().isOk());
    }

    private String createOrder(UUID ownerId, String key, String plan) throws Exception {
        String response = mockMvc.perform(post("/api/v2/payments/orders")
                        .header("X-Service-Token",
                                "payment-gateway-test-token-0000000000000001")
                        .header("X-Payment-Owner", ownerId)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pricingPlanId":"%s","billingCountry":"GB",
                                 "immediateSupplyRequested":true,
                                 "cancellationRightLossAcknowledged":true}
                                """.formatted(plan)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("orderId").asText();
    }

    private void bindOrder(UUID ownerId, String orderId, String sessionId)
            throws Exception {
        mockMvc.perform(post("/api/v2/payments/orders/{orderId}/bind-stripe-session", orderId)
                        .header("X-Service-Token",
                                "stripe-gateway-test-token-0000000000000001")
                        .header("X-Payment-Owner", ownerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stripeSessionId\":\"" + sessionId + "\"}"))
                .andExpect(status().isOk());
    }

    private AiTokenWallet wallet(UUID walletId, long balance) {
        return wallet(walletId, "fixture-owner", balance);
    }

    private AiTokenWallet wallet(
            UUID walletId, String ownerId, long balance) {
        return AiTokenWallet.builder()
                .id(walletId)
                .userId(ownerId)
                .balanceTokens(balance)
                .lifetimePurchasedTokens(20_000)
                .lifetimeSpentTokens(8_000)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-02T00:00:00Z"))
                .build();
    }

    private AiTokenTransaction entry(
            UUID walletId,
            TransactionType type,
            long delta,
            long before,
            long after,
            String operationId,
            Instant createdAt) {
        return entry(
                walletId,
                "fixture-owner",
                type,
                delta,
                before,
                after,
                operationId,
                createdAt);
    }

    private AiTokenTransaction entry(
            UUID walletId,
            String ownerId,
            TransactionType type,
            long delta,
            long before,
            long after,
            String operationId,
            Instant createdAt) {
        return AiTokenTransaction.builder()
                .id(UUID.randomUUID())
                .userId(ownerId)
                .walletId(walletId)
                .transactionType(type)
                .tokenAmount(Math.abs(delta))
                .balanceDeltaTokens(delta)
                .balanceBefore(before)
                .balanceAfter(after)
                .operationId(operationId)
                .createdAt(createdAt)
                .build();
    }

    private UUID syntheticOwner(String scenarioId, String identityKey) {
        return UUID.nameUUIDFromBytes(("job-seeker-copilot:system-data:"
                + scenarioId
                + ":"
                + identityKey
                + ":user").getBytes(StandardCharsets.UTF_8));
    }
}
