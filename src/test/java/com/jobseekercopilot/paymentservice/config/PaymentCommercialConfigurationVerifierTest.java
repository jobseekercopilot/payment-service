package com.jobseekercopilot.paymentservice.config;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentCommercialConfigurationVerifierTest {

    @Test
    void productionCanBeStagedExplicitlyUnconfiguredWhileCheckoutIsDisabled() {
        PaymentProperties properties = approvedProperties();
        properties.getCheckout().setEnabled(false);
        properties.getCheckout().setReleaseAuthorised(false);
        properties.getPromotion().setEnabled(false);
        properties.setTaxStatus(PaymentProperties.TaxStatus.NOT_CONFIGURED);
        properties.getLegalEntity().setType(PaymentProperties.LegalEntityType.NOT_CONFIGURED);
        properties.getLegalEntity().setConfigurationVersion("NOT_CONFIGURED");
        properties.getLegalEntity().setReviewed(false);

        assertThatCode(() -> verifier(properties, productionEnvironment(
                        false, false, "NOT_CONFIGURED", "NOT_CONFIGURED", false)).run(
                        new DefaultApplicationArguments(new String[0])))
                .doesNotThrowAnyException();
    }

    @Test
    void productionCheckoutRejectsAnUnreviewedSellerConfiguration() {
        PaymentProperties properties = approvedProperties();
        properties.getCheckout().setEnabled(true);
        properties.getCheckout().setReleaseAuthorised(true);
        properties.getPromotion().setEnabled(false);
        properties.setTaxStatus(PaymentProperties.TaxStatus.NOT_VAT_REGISTERED);
        properties.getLegalEntity().setType(PaymentProperties.LegalEntityType.SOLE_TRADER);
        properties.getLegalEntity().setConfigurationVersion("seller-v1");
        properties.getLegalEntity().setReviewed(false);

        assertThatThrownBy(() -> verifier(properties, productionEnvironment(
                        true, true, "NOT_VAT_REGISTERED", "SOLE_TRADER", false)).run(
                        new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reviewed legal-entity and tax configuration");
    }

    @Test
    void notVatRegisteredCannotAdvertiseVatIncluded() {
        PaymentProperties properties = approvedProperties();
        properties.setTaxStatus(PaymentProperties.TaxStatus.NOT_VAT_REGISTERED);
        properties.setTaxTreatment(PaymentProperties.TaxTreatment.VAT_INCLUDED);

        assertThatThrownBy(() -> verifier(properties, new MockEnvironment()).run(
                        new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NOT_VAT_REGISTERED requires VAT_NOT_CHARGED");
    }

    @Test
    void reviewedSellerConfigurationRejectsAnUnsafeVersion() {
        PaymentProperties properties = approvedProperties();
        properties.getLegalEntity().setType(PaymentProperties.LegalEntityType.SOLE_TRADER);
        properties.getLegalEntity().setConfigurationVersion("seller version with spaces");
        properties.getLegalEntity().setReviewed(true);

        assertThatThrownBy(() -> verifier(properties, new MockEnvironment()).run(
                        new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid format");
    }

    @Test
    void checkoutOrderWindowLeavesTimeForStripesMinimumSessionLifetime() {
        PaymentProperties properties = approvedProperties();
        properties.getCheckout().setOrderTtl(Duration.ofMinutes(30));

        assertThatThrownBy(() -> verifier(properties, new MockEnvironment()).run(
                        new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("between 1 and 24 hours");
    }

    private PaymentCommercialConfigurationVerifier verifier(
            PaymentProperties properties, MockEnvironment environment) {
        return new PaymentCommercialConfigurationVerifier(properties, environment);
    }

    private MockEnvironment productionEnvironment(
            boolean checkoutEnabled,
            boolean releaseAuthorised,
            String taxStatus,
            String legalEntityType,
            boolean legalEntityReviewed) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("production");
        environment.setProperty("PAYMENT_CHECKOUT_ENABLED", Boolean.toString(checkoutEnabled));
        environment.setProperty(
                "PAYMENT_CHECKOUT_RELEASE_AUTHORISED", Boolean.toString(releaseAuthorised));
        environment.setProperty("PAYMENT_PROVIDER_LIVE_MODE_EXPECTED", "false");
        environment.setProperty("PAYMENT_TAX_TREATMENT", "VAT_NOT_CHARGED");
        environment.setProperty("PAYMENT_TAX_STATUS", taxStatus);
        environment.setProperty("PAYMENT_LEGAL_ENTITY_TYPE", legalEntityType);
        environment.setProperty(
                "PAYMENT_LEGAL_ENTITY_CONFIGURATION_VERSION",
                "NOT_CONFIGURED".equals(legalEntityType) ? "NOT_CONFIGURED" : "seller-v1");
        environment.setProperty(
                "PAYMENT_LEGAL_ENTITY_REVIEWED", Boolean.toString(legalEntityReviewed));
        environment.setProperty("PAYMENT_FOUNDING_PROMOTION_ENABLED", "false");
        environment.setProperty("PAYMENT_FOUNDING_PROMOTION_RELEASE_AUTHORISED", "false");
        environment.setProperty("PAYMENT_CATALOG_VERSION", "public-beta-2026-08-15");
        environment.setProperty(
                "PAYMENT_CONSUMER_TERMS_VERSION", "uk-consumer-terms-2026-08-15");
        environment.setProperty("PAYMENT_FINANCIAL_RECORD_RETENTION_YEARS", "7");
        environment.setProperty(
                "PAYMENT_STRIPE_LIFECYCLE_GATEWAY_URL", "http://stripe-gateway:8100");
        environment.setProperty(
                "PAYMENT_SERVICE_TO_STRIPE_GATEWAY_LIFECYCLE_TOKEN",
                "payment-to-stripe-lifecycle-test-token-000001");
        environment.setProperty("PAYMENT_PROVIDER_SESSION_RECOVERY_ENABLED", "true");
        return environment;
    }

    private PaymentProperties approvedProperties() {
        PaymentProperties properties = new PaymentProperties();
        properties.getAccountLifecycle().setStripeGatewayUrl("http://stripe-gateway:8100");
        properties.getAccountLifecycle().setStripeGatewayToken(
                "payment-to-stripe-lifecycle-test-token-000001");
        properties.setPricingPlans(List.of(
                plan("starter", "Starter", "Up to 5 complete CV and cover-letter applications",
                        10, 799, 1),
                plan("active", "Active", "25 tailored document credits", 25, 1699, 2),
                plan("power", "Power", "60 tailored document credits", 60, 3499, 3)));
        return properties;
    }

    private PaymentProperties.PricingPlan plan(
            String id, String name, String description, int credits, long priceMinor, int order) {
        PaymentProperties.PricingPlan plan = new PaymentProperties.PricingPlan();
        plan.setId(id);
        plan.setName(name);
        plan.setDescription(description);
        plan.setDocumentCredits(credits);
        plan.setPriceGbpPence(priceMinor);
        plan.setActive(true);
        plan.setSortOrder(order);
        return plan;
    }
}
