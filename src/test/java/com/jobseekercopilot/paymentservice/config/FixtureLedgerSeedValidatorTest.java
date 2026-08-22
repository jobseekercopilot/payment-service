package com.jobseekercopilot.paymentservice.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobseekercopilot.paymentservice.entity.AiTokenTransaction;
import com.jobseekercopilot.paymentservice.entity.AiTokenWallet;
import com.jobseekercopilot.paymentservice.entity.TransactionType;
import com.jobseekercopilot.paymentservice.exception.BadRequestException;
import com.jobseekercopilot.paymentservice.systemdata.FixtureLedgerSeedValidator;
import com.jobseekercopilot.paymentservice.systemdata.SystemDataPaymentSeedRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FixtureLedgerSeedValidatorTest {
    private final FixtureLedgerSeedValidator validator = new FixtureLedgerSeedValidator();

    @Test
    void acceptsAnIsolatedFixtureWithACompleteReconciledLedger() {
        UUID walletId = UUID.randomUUID();
        SystemDataPaymentSeedRequest request = request(
                wallet(walletId, 12_000),
                List.of(
                        entry(walletId, 0, 20_000, 20_000, "grant", Instant.parse("2026-01-01T00:00:00Z")),
                        entry(walletId, 20_000, 12_000, -8_000, "spend", Instant.parse("2026-01-02T00:00:00Z"))));

        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
    }

    @Test
    void rejectsAFixtureWhoseWalletDoesNotReconcile() {
        UUID walletId = UUID.randomUUID();
        SystemDataPaymentSeedRequest request = request(
                wallet(walletId, 12_001),
                List.of(entry(walletId, 0, 12_000, 12_000, "grant", Instant.now())));

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("does not reconcile");
    }

    @Test
    void rejectsAFixtureWhoseEntriesDoNotFormOneChain() {
        UUID walletId = UUID.randomUUID();
        SystemDataPaymentSeedRequest request = request(
                wallet(walletId, 10_000),
                List.of(
                        entry(walletId, 0, 20_000, 20_000, "grant", Instant.parse("2026-01-01T00:00:00Z")),
                        entry(walletId, 19_000, 10_000, -9_000, "spend", Instant.parse("2026-01-02T00:00:00Z"))));

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("chronological balance chain");
    }

    private SystemDataPaymentSeedRequest request(
            AiTokenWallet wallet, List<AiTokenTransaction> transactions) {
        return new SystemDataPaymentSeedRequest(
                "scenario", "fixture-owner", wallet, transactions, List.of());
    }

    private AiTokenWallet wallet(UUID walletId, long balance) {
        return AiTokenWallet.builder()
                .id(walletId)
                .userId("fixture-owner")
                .balanceTokens(balance)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-02T00:00:00Z"))
                .build();
    }

    private AiTokenTransaction entry(
            UUID walletId,
            long before,
            long after,
            long delta,
            String operationId,
            Instant createdAt) {
        return AiTokenTransaction.builder()
                .id(UUID.randomUUID())
                .userId("fixture-owner")
                .walletId(walletId)
                .transactionType(delta >= 0 ? TransactionType.ADJUSTMENT : TransactionType.SPEND)
                .tokenAmount(Math.abs(delta))
                .balanceDeltaTokens(delta)
                .balanceBefore(before)
                .balanceAfter(after)
                .operationId(operationId)
                .createdAt(createdAt)
                .build();
    }
}
