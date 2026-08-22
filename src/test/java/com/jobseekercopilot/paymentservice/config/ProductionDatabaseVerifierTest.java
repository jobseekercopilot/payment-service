package com.jobseekercopilot.paymentservice.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

class ProductionDatabaseVerifierTest {
    private static final DefaultApplicationArguments NO_ARGUMENTS =
            new DefaultApplicationArguments(new String[0]);

    @Test
    void rejectsNonPostgresProductionDatabase() {
        MockEnvironment environment = validEnvironment()
                .withProperty("spring.datasource.url", "jdbc:h2:mem:unsafe");

        assertThatThrownBy(() -> verifier(environment).run(NO_ARGUMENTS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requires PostgreSQL");
    }

    @Test
    void rejectsPostgresWithoutVerifiedTls() {
        MockEnvironment environment = validEnvironment()
                .withProperty(
                        "spring.datasource.url",
                        "jdbc:postgresql://database.example/payment");

        assertThatThrownBy(() -> verifier(environment).run(NO_ARGUMENTS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sslmode=verify-full");
    }

    @Test
    void rejectsLookalikeTlsQueryParameter() {
        MockEnvironment environment = validEnvironment()
                .withProperty(
                        "spring.datasource.url",
                        "jdbc:postgresql://database.example/payment?unsafe=sslmode=verify-fullish");

        assertThatThrownBy(() -> verifier(environment).run(NO_ARGUMENTS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sslmode=verify-full");
    }

    @Test
    void rejectsDisabledMigrations() {
        MockEnvironment environment = validEnvironment()
                .withProperty(
                        "spring.datasource.url",
                        "jdbc:postgresql://database.example/payment?sslmode=verify-full")
                .withProperty("spring.flyway.enabled", "false");

        assertThatThrownBy(() -> verifier(environment).run(NO_ARGUMENTS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Flyway migrations");
    }

    @Test
    void rejectsDisabledStartupReconciliation() {
        MockEnvironment environment = validEnvironment()
                .withProperty(
                        "spring.datasource.url",
                        "jdbc:postgresql://database.example/payment?sslmode=verify-full");
        PaymentProperties properties = new PaymentProperties();
        properties.getLedger().setVerifyOnStartup(false);

        assertThatThrownBy(() -> new ProductionDatabaseVerifier(properties, environment)
                        .run(NO_ARGUMENTS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Startup ledger reconciliation");
    }

    @Test
    void rejectsDisabledExpiredReservationRecovery() {
        MockEnvironment environment = validEnvironment()
                .withProperty(
                        "spring.datasource.url",
                        "jdbc:postgresql://database.example/payment?sslmode=verify-full");
        PaymentProperties properties = new PaymentProperties();
        properties.getReservationRecovery().setEnabled(false);

        assertThatThrownBy(() -> new ProductionDatabaseVerifier(properties, environment)
                        .run(NO_ARGUMENTS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expired reservation recovery");
    }

    @Test
    void acceptsPostgresWithVerifiedTlsAndMigrationOnlySchemaManagement() {
        MockEnvironment environment = validEnvironment()
                .withProperty(
                        "spring.datasource.url",
                        "jdbc:postgresql://database.example/payment?sslmode=verify-full");

        assertThatCode(() -> verifier(environment).run(NO_ARGUMENTS))
                .doesNotThrowAnyException();
    }

    @Test
    void verifiesSafetyBeforeRunningMigrations() {
        MockEnvironment environment = validEnvironment()
                .withProperty(
                        "spring.datasource.url",
                        "jdbc:postgresql://database.example/payment?sslmode=verify-full");
        Flyway flyway = mock(Flyway.class);

        verifier(environment).migrate(flyway);

        verify(flyway).migrate();
    }

    @Test
    void neverRunsMigrationsForAnUnsafeDatabase() {
        MockEnvironment environment = validEnvironment()
                .withProperty(
                        "spring.datasource.url",
                        "jdbc:postgresql://database.example/payment");
        Flyway flyway = mock(Flyway.class);

        assertThatThrownBy(() -> verifier(environment).migrate(flyway))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sslmode=verify-full");
        verify(flyway, never()).migrate();
    }

    @Test
    void isolatedTestConfigurationCanExplicitlyDisableProductionCheck() {
        PaymentProperties properties = new PaymentProperties();
        properties.getDatabase().setProductionSafetyCheck(false);

        assertThatCode(() -> new ProductionDatabaseVerifier(
                                properties, new MockEnvironment())
                        .run(NO_ARGUMENTS))
                .doesNotThrowAnyException();
    }

    private MockEnvironment validEnvironment() {
        return new MockEnvironment()
                .withProperty("spring.flyway.enabled", "true")
                .withProperty("spring.jpa.hibernate.ddl-auto", "validate")
                .withProperty("spring.h2.console.enabled", "false");
    }

    private ProductionDatabaseVerifier verifier(MockEnvironment environment) {
        return new ProductionDatabaseVerifier(new PaymentProperties(), environment);
    }
}
