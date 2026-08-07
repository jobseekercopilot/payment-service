package com.jobseekercopilot.paymentservice.systemdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseekercopilot.paymentservice.entity.AiTokenTransaction;
import com.jobseekercopilot.paymentservice.entity.AiTokenWallet;
import com.jobseekercopilot.paymentservice.entity.TransactionType;
import com.jobseekercopilot.paymentservice.repository.AiTokenTransactionRepository;
import com.jobseekercopilot.paymentservice.repository.AiTokenWalletRepository;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class PostgresPaymentSystemDataResetIntegrationTest {
    private static final String ENVIRONMENT_DATA_TOKEN =
            "postgres-environment-data-token-00000001";
    private static final String WRONG_TOKEN =
            "wrong-postgres-environment-token-000001";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15.18-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add(
                "spring.flyway.locations",
                () -> "classpath:db/migration/common,classpath:db/migration/postgresql-live");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("payment.database.production-safety-check", () -> "false");
        registry.add("payment.ledger.verify-on-startup", () -> "true");
        registry.add("payment.reservation-recovery.enabled", () -> "false");
        registry.add("environment-data.enabled", () -> "true");
        registry.add("environment-data.isolated-database", () -> "true");
        registry.add("environment-data.allowed-environments", () -> "test");
        registry.add("environment-data.token", () -> ENVIRONMENT_DATA_TOKEN);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EnvironmentLedgerReset environmentLedgerReset;
    @Autowired private AiTokenWalletRepository walletRepository;
    @Autowired private AiTokenTransactionRepository transactionRepository;

    @Test
    void seedResetAndReseedUsesAnOwnerBoundTransactionLocalEscape() throws Exception {
        String owner = uniqueOwner("repeatable-reset");
        SystemDataPaymentSeedRequest request = fixture(owner);

        seed(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.details.ledgerEntries").value(2));
        mockMvc.perform(delete("/internal/system-data/scenario/{scenarioId}/payments/{owner}",
                        "scenario-v1", owner)
                        .header(EnvironmentDataGuard.TOKEN_HEADER, ENVIRONMENT_DATA_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.details.wallets").value(1))
                .andExpect(jsonPath("$.details.ledgerEntries").value(2));
        assertThat(walletRepository.findByUserId(owner)).isEmpty();
        assertThat(transactionRepository.findByUserId(owner)).isEmpty();

        seed(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.details.ledgerEntries").value(2));
        assertAppendOnlyFailure(() -> transactionTemplate().executeWithoutResult(
                ignored -> jdbcTemplate.update(
                        "DELETE FROM ai_token_transactions WHERE user_id = ?", owner)));
        assertThat(transactionRepository.findByUserId(owner)).hasSize(2);
    }

    @Test
    void resetCannotCrossTheExactOwnerBoundary() throws Exception {
        String owner = uniqueOwner("owned-ledger");
        String differentOwner = uniqueOwner("different-owner");
        seed(fixture(owner)).andExpect(status().isOk());

        mockMvc.perform(delete("/internal/system-data/scenario/{scenarioId}/payments/{owner}",
                        "scenario-v1", differentOwner)
                        .header(EnvironmentDataGuard.TOKEN_HEADER, ENVIRONMENT_DATA_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordsAffected").value(0));
        assertThat(transactionRepository.findByUserId(owner)).hasSize(2);

        assertAppendOnlyFailure(() -> transactionTemplate().executeWithoutResult(ignored -> {
            setLocalResetOwner(differentOwner);
            jdbcTemplate.update(
                    "DELETE FROM ai_token_transactions WHERE user_id = ?", owner);
        }));
        assertThat(transactionRepository.findByUserId(owner)).hasSize(2);
    }

    @Test
    void updateAndOrdinaryDeleteRemainAppendOnly() throws Exception {
        String owner = uniqueOwner("immutable-ledger");
        seed(fixture(owner)).andExpect(status().isOk());

        assertAppendOnlyFailure(() -> transactionTemplate().executeWithoutResult(ignored -> {
            setLocalResetOwner(owner);
            jdbcTemplate.update(
                    "UPDATE ai_token_transactions SET description = ? WHERE user_id = ?",
                    "must fail", owner);
        }));
        assertAppendOnlyFailure(() -> transactionTemplate().executeWithoutResult(
                ignored -> jdbcTemplate.update(
                        "DELETE FROM ai_token_transactions WHERE user_id = ?", owner)));
        assertThat(transactionRepository.findByUserId(owner)).hasSize(2);
    }

    @Test
    void rollbackClearsTheResetOwnerBeforeTheConnectionReturnsToThePool()
            throws Exception {
        String owner = uniqueOwner("rollback-reset");
        seed(fixture(owner)).andExpect(status().isOk());

        transactionTemplate().executeWithoutResult(status -> {
            assertThat(environmentLedgerReset.deleteOwnerLedger(owner)).isEqualTo(2);
            assertThat(transactionRepository.findByUserId(owner)).isEmpty();
            status.setRollbackOnly();
        });

        assertThat(transactionRepository.findByUserId(owner)).hasSize(2);
        assertTransactionLocalSettingClearsOnTheSameSession(owner);
        assertAppendOnlyFailure(() -> transactionTemplate().executeWithoutResult(
                ignored -> jdbcTemplate.update(
                        "DELETE FROM ai_token_transactions WHERE user_id = ?", owner)));
        assertThat(transactionRepository.findByUserId(owner)).hasSize(2);
    }

    @Test
    void systemDataEndpointsRequireExactlyOneIndependentCredential()
            throws Exception {
        String owner = uniqueOwner("credential-check");
        String path = "/internal/system-data/verify/payments/" + owner;

        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ENVIRONMENT_DATA_UNAUTHORIZED"));
        mockMvc.perform(get(path)
                        .header(EnvironmentDataGuard.TOKEN_HEADER, WRONG_TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ENVIRONMENT_DATA_UNAUTHORIZED"));
        mockMvc.perform(get(path)
                        .header(
                                EnvironmentDataGuard.TOKEN_HEADER,
                                ENVIRONMENT_DATA_TOKEN,
                                ENVIRONMENT_DATA_TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ENVIRONMENT_DATA_UNAUTHORIZED"));
        mockMvc.perform(get(path)
                        .header(EnvironmentDataGuard.TOKEN_HEADER, ENVIRONMENT_DATA_TOKEN))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions seed(
            SystemDataPaymentSeedRequest request) throws Exception {
        return mockMvc.perform(post("/internal/system-data/seed/payments")
                .header(EnvironmentDataGuard.TOKEN_HEADER, ENVIRONMENT_DATA_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)));
    }

    private SystemDataPaymentSeedRequest fixture(String owner) {
        UUID walletId = UUID.randomUUID();
        return new SystemDataPaymentSeedRequest(
                "scenario-v1",
                owner,
                AiTokenWallet.builder()
                        .id(walletId)
                        .userId(owner)
                        .balanceTokens(12_000)
                        .lifetimePurchasedTokens(20_000)
                        .lifetimeSpentTokens(8_000)
                        .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                        .updatedAt(Instant.parse("2026-01-02T00:00:00Z"))
                        .build(),
                List.of(
                        entry(
                                owner,
                                walletId,
                                TransactionType.DEMO_PURCHASE,
                                20_000,
                                0,
                                20_000,
                                "FIXTURE:scenario-v1:purchase"),
                        entry(
                                owner,
                                walletId,
                                TransactionType.SPEND,
                                -8_000,
                                20_000,
                                12_000,
                                "FIXTURE:scenario-v1:spend")),
                List.of());
    }

    private AiTokenTransaction entry(
            String owner,
            UUID walletId,
            TransactionType type,
            long delta,
            long before,
            long after,
            String operationId) {
        return AiTokenTransaction.builder()
                .id(UUID.randomUUID())
                .userId(owner)
                .walletId(walletId)
                .transactionType(type)
                .tokenAmount(Math.abs(delta))
                .balanceDeltaTokens(delta)
                .balanceBefore(before)
                .balanceAfter(after)
                .operationId(operationId)
                .createdAt(Instant.parse(
                        type == TransactionType.DEMO_PURCHASE
                                ? "2026-01-01T00:00:00Z"
                                : "2026-01-02T00:00:00Z"))
                .build();
    }

    private void setLocalResetOwner(String owner) {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT set_config(?, ?, true)",
                String.class,
                EnvironmentLedgerReset.RESET_OWNER_SETTING,
                owner)).isEqualTo(owner);
    }

    private void assertTransactionLocalSettingClearsOnTheSameSession(String owner)
            throws SQLException {
        try (java.sql.Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            setLocalResetOwner(connection, owner);
            connection.rollback();
            assertThat(currentResetOwner(connection)).isNullOrEmpty();

            setLocalResetOwner(connection, owner);
            connection.commit();
            assertThat(currentResetOwner(connection)).isNullOrEmpty();

            Throwable failure = catchThrowable(() -> {
                try (java.sql.PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM ai_token_transactions WHERE user_id = ?")) {
                    statement.setString(1, owner);
                    statement.executeUpdate();
                }
            });
            assertThat(sqlState(failure)).isEqualTo("55000");
            connection.rollback();
        }
    }

    private void setLocalResetOwner(java.sql.Connection connection, String owner)
            throws SQLException {
        try (java.sql.PreparedStatement statement = connection.prepareStatement(
                "SELECT set_config(?, ?, true)")) {
            statement.setString(1, EnvironmentLedgerReset.RESET_OWNER_SETTING);
            statement.setString(2, owner);
            try (java.sql.ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo(owner);
            }
        }
    }

    private String currentResetOwner(java.sql.Connection connection)
            throws SQLException {
        try (java.sql.PreparedStatement statement = connection.prepareStatement(
                "SELECT current_setting(?, true)")) {
            statement.setString(1, EnvironmentLedgerReset.RESET_OWNER_SETTING);
            try (java.sql.ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString(1);
            }
        }
    }

    private void assertAppendOnlyFailure(ThrowingOperation operation) {
        Throwable failure = catchThrowable(operation::run);
        assertThat(failure).isNotNull();
        assertThat(sqlState(failure)).isEqualTo("55000");
    }

    private String sqlState(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                return sqlException.getSQLState();
            }
            current = current.getCause();
        }
        return null;
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    private String uniqueOwner(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run() throws Exception;
    }
}
