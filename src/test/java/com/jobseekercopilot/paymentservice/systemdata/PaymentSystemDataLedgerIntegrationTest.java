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
    "environment-data.token=environment-data-integration-token-00000001"
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
    @Autowired private LedgerReconciliationService reconciliationService;

    @BeforeEach
    void clean() {
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
