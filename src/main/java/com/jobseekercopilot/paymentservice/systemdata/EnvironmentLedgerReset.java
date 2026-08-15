package com.jobseekercopilot.paymentservice.systemdata;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Component
public class EnvironmentLedgerReset {
    static final String RESET_OWNER_SETTING =
            "jobseekercopilot.environment_data_reset_owner";

    private final EnvironmentDataGuard guard;
    private final JdbcTemplate jdbcTemplate;

    public EnvironmentLedgerReset(
            EnvironmentDataGuard guard,
            JdbcTemplate jdbcTemplate) {
        this.guard = guard;
        this.jdbcTemplate = jdbcTemplate;
    }

    public int deleteOwnerLedger(String ownerId) {
        guard.requireEnabled();
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Environment ledger reset requires an active transaction");
        }
        if (!StringUtils.hasText(ownerId)) {
            throw new IllegalArgumentException("Environment ledger reset owner is required");
        }
        return jdbcTemplate.execute((ConnectionCallback<Integer>) connection -> {
            String databaseProduct = connection.getMetaData().getDatabaseProductName();
            if ("PostgreSQL".equals(databaseProduct)) {
                setTransactionLocalOwner(connection, ownerId);
            } else if (!"H2".equals(databaseProduct)) {
                throw new SQLException(
                        "Environment ledger reset is unsupported for database product "
                                + databaseProduct);
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM ai_token_transactions WHERE user_id = ?")) {
                statement.setString(1, ownerId);
                return statement.executeUpdate();
            }
        });
    }

    /**
     * Removes the v2 payment aggregate for one deterministic owner in an
     * isolated environment-data database. The database trigger accepts the
     * document-credit ledger delete only while the same transaction-local,
     * exact-owner guard used by the legacy ledger is present.
     */
    public DocumentCreditResetCounts deleteOwnerDocumentCreditData(String ownerId) {
        guard.requireEnabled();
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Environment document-credit reset requires an active transaction");
        }
        if (!StringUtils.hasText(ownerId)) {
            throw new IllegalArgumentException(
                    "Environment document-credit reset owner is required");
        }
        return jdbcTemplate.execute((ConnectionCallback<DocumentCreditResetCounts>) connection -> {
            String databaseProduct = connection.getMetaData().getDatabaseProductName();
            if ("PostgreSQL".equals(databaseProduct)) {
                setTransactionLocalOwner(connection, ownerId);
            } else if (!"H2".equals(databaseProduct)) {
                throw new SQLException(
                        "Environment document-credit reset is unsupported for database product "
                                + databaseProduct);
            }
            int providerEvents = delete(connection,
                    "DELETE FROM payment_provider_events WHERE order_id IN "
                            + "(SELECT id FROM payment_orders WHERE user_id = ?)", ownerId);
            int promotionReservations = delete(connection,
                    "DELETE FROM founding_promotion_reservations WHERE user_id = ?", ownerId);
            int orders = delete(connection,
                    "DELETE FROM payment_orders WHERE user_id = ?", ownerId);
            int reservations = delete(connection,
                    "DELETE FROM document_credit_reservations WHERE user_id = ?", ownerId);
            int transactions = delete(connection,
                    "DELETE FROM document_credit_transactions WHERE user_id = ?", ownerId);
            int wallets = delete(connection,
                    "DELETE FROM document_credit_wallets WHERE user_id = ?", ownerId);
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE founding_promotion_campaigns campaign SET "
                            + "active_reservations = (SELECT COUNT(*) FROM founding_promotion_reservations "
                            + "WHERE campaign_id = campaign.id AND status = 'RESERVED'), "
                            + "completed_claims = (SELECT COUNT(*) FROM founding_promotion_reservations "
                            + "WHERE campaign_id = campaign.id AND status = 'COMPLETED'), "
                            + "updated_at = CURRENT_TIMESTAMP")) {
                statement.executeUpdate();
            }
            return new DocumentCreditResetCounts(
                    wallets, transactions, reservations, orders,
                    providerEvents, promotionReservations);
        });
    }

    private int delete(Connection connection, String sql, String ownerId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ownerId);
            return statement.executeUpdate();
        }
    }

    private void setTransactionLocalOwner(Connection connection, String ownerId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT set_config('" + RESET_OWNER_SETTING + "', ?, true)")) {
            statement.setString(1, ownerId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || !ownerId.equals(result.getString(1))) {
                    throw new SQLException(
                            "Could not establish transaction-local environment reset owner");
                }
            }
        }
    }

    public record DocumentCreditResetCounts(
            int wallets,
            int ledgerEntries,
            int reservations,
            int orders,
            int providerEvents,
            int promotionReservations) {
        public int total() {
            return wallets + ledgerEntries + reservations + orders
                    + providerEvents + promotionReservations;
        }
    }
}
