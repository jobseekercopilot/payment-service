package com.jobseekercopilot.paymentservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PostgresLedgerMigrationTest {
    private static final String PRIMARY_DATABASE = "payment";
    private static final String RESTORED_DATABASE = "payment_restore";

    @org.testcontainers.junit.jupiter.Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15.18-alpine3.23")
                    .withDatabaseName(PRIMARY_DATABASE)
                    .withUsername("payment")
                    .withPassword("payment");

    @Test
    void migrationsPersistLedgerAndDatabaseRejectsMutation() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations(
                        "classpath:db/migration/common",
                        "classpath:db/migration/postgresql-live")
                .load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(11);
        flyway.validate();

        UUID walletId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        try (Connection connection = connection()) {
            insertWallet(connection, walletId);
            insertLedgerEntry(connection, walletId, entryId);
        }

        try (Connection restartedConnection = connection()) {
            assertReconciled(restartedConnection, walletId);
            assertThat(queryLong(
                            restartedConnection,
                            "SELECT sequence_number FROM ai_token_transactions WHERE id = ?",
                            entryId))
                    .isPositive();

            assertThatThrownBy(() -> updateLedgerEntry(restartedConnection, entryId))
                    .isInstanceOf(SQLException.class)
                    .extracting(exception -> ((SQLException) exception).getSQLState())
                    .isEqualTo("55000");
            assertThatThrownBy(() -> deleteLedgerEntry(restartedConnection, entryId))
                    .isInstanceOf(SQLException.class)
                    .extracting(exception -> ((SQLException) exception).getSQLState())
                    .isEqualTo("55000");
            assertThatThrownBy(
                            () -> insertInvalidLedgerEntry(
                                    restartedConnection, walletId, UUID.randomUUID()))
                    .isInstanceOf(SQLException.class)
                    .extracting(exception -> ((SQLException) exception).getSQLState())
                    .isEqualTo("23514");
        }

        assertExecSucceeded(POSTGRES.execInContainer(
                "pg_dump",
                "--username=" + POSTGRES.getUsername(),
                "--format=custom",
                "--file=/tmp/payment.dump",
                PRIMARY_DATABASE));
        assertExecSucceeded(POSTGRES.execInContainer(
                "createdb",
                "--username=" + POSTGRES.getUsername(),
                RESTORED_DATABASE));
        assertExecSucceeded(POSTGRES.execInContainer(
                "pg_restore",
                "--username=" + POSTGRES.getUsername(),
                "--dbname=" + RESTORED_DATABASE,
                "--no-owner",
                "/tmp/payment.dump"));

        Flyway.configure()
                .dataSource(
                        restoredJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations(
                        "classpath:db/migration/common",
                        "classpath:db/migration/postgresql-live")
                .load()
                .validate();
        try (Connection restoredConnection = restoredConnection()) {
            assertReconciled(restoredConnection, walletId);
            assertThatThrownBy(() -> updateLedgerEntry(restoredConnection, entryId))
                    .isInstanceOf(SQLException.class)
                    .extracting(exception -> ((SQLException) exception).getSQLState())
                    .isEqualTo("55000");
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private Connection restoredConnection() throws SQLException {
        return DriverManager.getConnection(
                restoredJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private String restoredJdbcUrl() {
        return "jdbc:postgresql://%s:%d/%s"
                .formatted(
                        POSTGRES.getHost(),
                        POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
                        RESTORED_DATABASE);
    }

    private void insertWallet(Connection connection, UUID walletId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO ai_token_wallets (
                    id, user_id, balance_tokens, lifetime_purchased_tokens,
                    lifetime_spent_tokens, lifetime_refunded_tokens,
                    free_trial_granted, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            statement.setObject(1, walletId);
            statement.setString(2, "migration-owner");
            statement.setLong(3, 20_000);
            statement.setLong(4, 0);
            statement.setLong(5, 0);
            statement.setLong(6, 0);
            statement.setBoolean(7, true);
            statement.setObject(8, now);
            statement.setObject(9, now);
            statement.executeUpdate();
        }
    }

    private void insertLedgerEntry(Connection connection, UUID walletId, UUID entryId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO ai_token_transactions (
                    id, user_id, wallet_id, transaction_type, token_amount,
                    balance_delta_tokens, balance_before, balance_after,
                    operation_id, description, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setObject(1, entryId);
            statement.setString(2, "migration-owner");
            statement.setObject(3, walletId);
            statement.setString(4, "FREE_TRIAL_GRANTED");
            statement.setLong(5, 20_000);
            statement.setLong(6, 20_000);
            statement.setLong(7, 0);
            statement.setLong(8, 20_000);
            statement.setString(9, "FREE_TRIAL:" + walletId);
            statement.setString(10, "Migration persistence proof");
            statement.setObject(11, OffsetDateTime.now(ZoneOffset.UTC));
            statement.executeUpdate();
        }
    }

    private void insertInvalidLedgerEntry(Connection connection, UUID walletId, UUID entryId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO ai_token_transactions (
                    id, user_id, wallet_id, transaction_type, token_amount,
                    balance_delta_tokens, balance_before, balance_after,
                    operation_id, description, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setObject(1, entryId);
            statement.setString(2, "migration-owner");
            statement.setObject(3, walletId);
            statement.setString(4, "ADJUSTMENT");
            statement.setLong(5, 1);
            statement.setLong(6, 1);
            statement.setLong(7, 20_000);
            statement.setLong(8, 20_000);
            statement.setString(9, "INVALID:" + entryId);
            statement.setString(10, "Must be rejected by the balance invariant");
            statement.setObject(11, OffsetDateTime.now(ZoneOffset.UTC));
            statement.executeUpdate();
        }
    }

    private void assertReconciled(Connection connection, UUID walletId) throws SQLException {
        long storedBalance = queryLong(
                connection,
                "SELECT balance_tokens FROM ai_token_wallets WHERE id = ?",
                walletId);
        long reconstructedBalance = queryLong(
                connection,
                "SELECT SUM(balance_delta_tokens) FROM ai_token_transactions WHERE wallet_id = ?",
                walletId);
        assertThat(storedBalance).isEqualTo(20_000);
        assertThat(reconstructedBalance).isEqualTo(storedBalance);
    }

    private void assertExecSucceeded(Container.ExecResult result) {
        assertThat(result.getExitCode())
                .withFailMessage(
                        "Container command failed:%nstdout:%n%s%nstderr:%n%s",
                        result.getStdout(), result.getStderr())
                .isZero();
    }

    private long queryLong(Connection connection, String sql, UUID id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getLong(1);
            }
        }
    }

    private void updateLedgerEntry(Connection connection, UUID entryId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE ai_token_transactions SET description = ? WHERE id = ?")) {
            statement.setString(1, "mutated");
            statement.setObject(2, entryId);
            statement.executeUpdate();
        }
    }

    private void deleteLedgerEntry(Connection connection, UUID entryId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM ai_token_transactions WHERE id = ?")) {
            statement.setObject(1, entryId);
            statement.executeUpdate();
        }
    }
}
