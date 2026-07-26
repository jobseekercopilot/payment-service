package com.jobseekercopilot.paymentservice.config;

import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.flywaydb.core.Flyway;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class ProductionDatabaseVerifier
        implements ApplicationRunner, FlywayMigrationStrategy {
    private static final Pattern VERIFIED_TLS_QUERY_PARAMETER =
            Pattern.compile("(?:[?&])sslmode=verify-full(?:&|$)", Pattern.CASE_INSENSITIVE);

    private final PaymentProperties paymentProperties;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        verifyConfiguration();
    }

    @Override
    public void migrate(Flyway flyway) {
        verifyConfiguration();
        flyway.migrate();
    }

    private void verifyConfiguration() {
        if (!paymentProperties.getDatabase().isProductionSafetyCheck()) {
            return;
        }

        String url = required("spring.datasource.url");
        if (!url.toLowerCase(Locale.ROOT).startsWith("jdbc:postgresql:")) {
            throw new IllegalStateException(
                    "Payment Service requires PostgreSQL outside isolated local/test profiles");
        }

        String lowerUrl = url.toLowerCase(Locale.ROOT);
        String configuredSslMode =
                environment.getProperty("spring.datasource.hikari.data-source-properties.sslmode", "");
        if (!VERIFIED_TLS_QUERY_PARAMETER.matcher(lowerUrl).find()
                && !"verify-full".equalsIgnoreCase(configuredSslMode)) {
            throw new IllegalStateException(
                    "Payment database connections must use sslmode=verify-full");
        }

        if (!environment.getProperty("spring.flyway.enabled", Boolean.class, false)) {
            throw new IllegalStateException(
                    "Reviewed Flyway migrations are required for the payment database");
        }
        if (!"validate".equalsIgnoreCase(required("spring.jpa.hibernate.ddl-auto"))) {
            throw new IllegalStateException(
                    "Hibernate schema mutation is forbidden; use reviewed Flyway migrations");
        }
        if (environment.getProperty("spring.h2.console.enabled", Boolean.class, false)) {
            throw new IllegalStateException("The H2 console is forbidden outside isolated tests");
        }
        if (!paymentProperties.getLedger().isVerifyOnStartup()) {
            throw new IllegalStateException(
                    "Startup ledger reconciliation is required outside isolated tests");
        }
        PaymentProperties.ReservationRecovery recovery =
                paymentProperties.getReservationRecovery();
        if (!recovery.isEnabled()) {
            throw new IllegalStateException(
                    "Expired reservation recovery is required outside isolated tests");
        }
        if (recovery.getTtl() == null
                || recovery.getTtl().isZero()
                || recovery.getTtl().isNegative()
                || recovery.getInterval() == null
                || recovery.getInterval().isZero()
                || recovery.getInterval().isNegative()
                || recovery.getBatchSize() < 1) {
            throw new IllegalStateException(
                    "Payment reservation recovery settings must be positive");
        }
    }

    private String required(String property) {
        String value = environment.getProperty(property);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required database setting: " + property);
        }
        return value;
    }
}
