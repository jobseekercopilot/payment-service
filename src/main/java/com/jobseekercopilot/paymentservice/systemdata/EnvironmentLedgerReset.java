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
}
