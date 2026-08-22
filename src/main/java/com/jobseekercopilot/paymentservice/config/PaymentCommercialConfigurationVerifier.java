package com.jobseekercopilot.paymentservice.config;

import java.time.Duration;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class PaymentCommercialConfigurationVerifier implements ApplicationRunner {
    private final PaymentProperties properties;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        verifyCatalog();
        verifyTax();
        verifyLegalEntity();
        verifyLifecycle();
        if (production()) verifyProductionReleaseControls();
    }

    private void verifyCatalog() {
        if (properties.getFreeDocumentCredits() != 2
                || !"GBP".equals(properties.getCurrency())
                || !"GB".equals(properties.getBillingCountry())
                || properties.getCatalogVersion() == null
                || properties.getCatalogVersion().isBlank()
                || properties.getConsumerTerms().getVersion() == null
                || properties.getConsumerTerms().getVersion().isBlank()
                || properties.getConsumerTerms().getVersion().length() > 64) {
            throw new IllegalStateException("The public-beta document-credit catalog is invalid");
        }
        Map<String, PaymentProperties.PricingPlan> plans = properties.activePricingPlans().stream()
                .collect(Collectors.toMap(PaymentProperties.PricingPlan::getId, Function.identity()));
        requirePlan(plans, "starter", 10, 499);
        requirePlan(plans, "active", 25, 1199);
        requirePlan(plans, "power", 60, 1999);
        if (plans.size() != 3) {
            throw new IllegalStateException("Only the approved public-beta packs may be active");
        }
        PaymentProperties.Promotion promotion = properties.getPromotion();
        if (!"founding-200".equals(promotion.getId())
                || promotion.getCustomerLimit() != 200
                || promotion.getBonusPercent() != 50) {
            throw new IllegalStateException("The founding promotion configuration is invalid");
        }
        Duration ttl = properties.getCheckout().getOrderTtl();
        if (ttl == null || ttl.isNegative() || ttl.isZero()
                || ttl.compareTo(Duration.ofHours(1)) < 0
                || ttl.compareTo(Duration.ofHours(24)) > 0) {
            throw new IllegalStateException("Checkout order TTL must be between 1 and 24 hours");
        }
    }

    private void requirePlan(
            Map<String, PaymentProperties.PricingPlan> plans,
            String id,
            int credits,
            long priceMinor) {
        PaymentProperties.PricingPlan plan = plans.get(id);
        if (plan == null
                || plan.getDocumentCredits() != credits
                || plan.getPriceGbpPence() != priceMinor
                || !approvedDescription(id).equals(plan.getDescription())) {
            throw new IllegalStateException("Approved public-beta plan mismatch: " + id);
        }
    }

    private String approvedDescription(String id) {
        return switch (id) {
            case "starter" -> "Up to 5 complete CV and cover-letter applications";
            case "active" -> "25 tailored document credits";
            case "power" -> "60 tailored document credits";
            default -> throw new IllegalArgumentException("Unknown public-beta plan: " + id);
        };
    }

    private void verifyTax() {
        switch (properties.getTaxStatus()) {
            case NOT_CONFIGURED -> {
                if (properties.getTaxTreatment() != PaymentProperties.TaxTreatment.VAT_NOT_CHARGED) {
                    throw new IllegalStateException(
                            "An unconfigured tax status cannot advertise VAT_INCLUDED");
                }
            }
            case NOT_VAT_REGISTERED -> {
                if (properties.getTaxTreatment() != PaymentProperties.TaxTreatment.VAT_NOT_CHARGED) {
                    throw new IllegalStateException(
                            "NOT_VAT_REGISTERED requires VAT_NOT_CHARGED");
                }
            }
            case VAT_REGISTERED -> {
                if (properties.getTaxTreatment() != PaymentProperties.TaxTreatment.VAT_INCLUDED) {
                    throw new IllegalStateException(
                            "VAT_REGISTERED consumer prices must use VAT_INCLUDED");
                }
            }
        }
    }

    private void verifyLegalEntity() {
        PaymentProperties.LegalEntity entity = properties.getLegalEntity();
        if (entity.isReviewed()
                && (entity.getType() == PaymentProperties.LegalEntityType.NOT_CONFIGURED
                || entity.getConfigurationVersion() == null
                || entity.getConfigurationVersion().isBlank())) {
            throw new IllegalStateException(
                    "A reviewed payment legal entity requires a type and configuration version");
        }
        if (entity.getConfigurationVersion() != null
                && !entity.getConfigurationVersion().isBlank()
                && !entity.getConfigurationVersion()
                .matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}")) {
            throw new IllegalStateException(
                    "Payment legal entity configuration version has an invalid format");
        }
    }

    private void verifyLifecycle() {
        if (properties.getRetention().getFinancialRecordYears() < 7) {
            throw new IllegalStateException(
                    "Payment reconciliation records must be retained for at least seven years");
        }
    }

    private void verifyProductionReleaseControls() {
        requireExplicitProductionSettings(
                "PAYMENT_CHECKOUT_ENABLED",
                "PAYMENT_CHECKOUT_RELEASE_AUTHORISED",
                "PAYMENT_PROVIDER_LIVE_MODE_EXPECTED",
                "PAYMENT_TAX_TREATMENT",
                "PAYMENT_TAX_STATUS",
                "PAYMENT_LEGAL_ENTITY_TYPE",
                "PAYMENT_LEGAL_ENTITY_CONFIGURATION_VERSION",
                "PAYMENT_LEGAL_ENTITY_REVIEWED",
                "PAYMENT_FOUNDING_PROMOTION_ENABLED",
                "PAYMENT_FOUNDING_PROMOTION_RELEASE_AUTHORISED",
                "PAYMENT_CATALOG_VERSION",
                "PAYMENT_CONSUMER_TERMS_VERSION",
                "PAYMENT_FINANCIAL_RECORD_RETENTION_YEARS",
                "PAYMENT_STRIPE_LIFECYCLE_GATEWAY_URL",
                "PAYMENT_SERVICE_TO_STRIPE_GATEWAY_LIFECYCLE_TOKEN",
                "PAYMENT_PROVIDER_SESSION_RECOVERY_ENABLED");
        verifyProductionAccountLifecycle();
        if (properties.isDemoPurchaseEnabled()
                || properties.isLegacyStripeConfirmationEnabled()) {
            throw new IllegalStateException(
                    "Demo and legacy Stripe balance mutation are forbidden in production");
        }
        if (properties.getCheckout().isEnabled()
                && !properties.getCheckout().isReleaseAuthorised()) {
            throw new IllegalStateException(
                    "Production Checkout requires explicit release authorisation");
        }
        if (properties.getCheckout().isEnabled()
                && properties.getCheckout().isReleaseAuthorised()
                && !commercialIdentityReady()) {
            throw new IllegalStateException(
                    "Production Checkout requires reviewed legal-entity and tax configuration");
        }
        if (properties.getCheckout().isProviderLiveModeExpected()
                && (!properties.getCheckout().isEnabled()
                || !properties.getCheckout().isReleaseAuthorised())) {
            throw new IllegalStateException(
                    "Expected live provider mode requires enabled, authorised Checkout");
        }
        if (properties.getPromotion().isEnabled()
                && (!properties.getPromotion().isReleaseAuthorised()
                || !properties.getCheckout().isEnabled()
                || !properties.getCheckout().isReleaseAuthorised())) {
            throw new IllegalStateException(
                    "Production founding promotion requires an authorised Checkout release");
        }
    }

    private boolean commercialIdentityReady() {
        return properties.getTaxStatus() != PaymentProperties.TaxStatus.NOT_CONFIGURED
                && properties.getLegalEntity().getType()
                != PaymentProperties.LegalEntityType.NOT_CONFIGURED
                && properties.getLegalEntity().isReviewed()
                && properties.getLegalEntity().getConfigurationVersion() != null
                && !properties.getLegalEntity().getConfigurationVersion().isBlank();
    }

    private void verifyProductionAccountLifecycle() {
        PaymentProperties.AccountLifecycle lifecycle = properties.getAccountLifecycle();
        if (!lifecycle.isProviderSessionRecoveryEnabled()) {
            throw new IllegalStateException(
                    "Production provider Checkout-session recovery must be enabled");
        }
        if (lifecycle.getStripeGatewayToken() == null
                || lifecycle.getStripeGatewayToken().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "Payment-to-Stripe lifecycle token must contain at least 32 bytes");
        }
        URI uri;
        try {
            uri = URI.create(lifecycle.getStripeGatewayUrl());
        } catch (RuntimeException invalid) {
            throw new IllegalStateException("Stripe lifecycle gateway URL is invalid", invalid);
        }
        if (uri.getScheme() == null
                || uri.getHost() == null
                || !(uri.getScheme().equalsIgnoreCase("http")
                || uri.getScheme().equalsIgnoreCase("https"))
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null
                || uri.getHost().equalsIgnoreCase("localhost")
                || uri.getHost().equals("127.0.0.1")) {
            throw new IllegalStateException(
                    "Production Stripe lifecycle gateway URL must be a non-local HTTP(S) origin");
        }
        if (lifecycle.getProviderSessionRecoveryInterval() == null
                || lifecycle.getProviderSessionRecoveryInterval().isNegative()
                || lifecycle.getProviderSessionRecoveryInterval().isZero()
                || lifecycle.getProviderSessionRecoveryBatchSize() < 1
                || lifecycle.getProviderSessionRecoveryBatchSize() > 500) {
            throw new IllegalStateException(
                    "Provider Checkout-session recovery bounds are invalid");
        }
    }

    private void requireExplicitProductionSettings(String... names) {
        List<String> missing = Arrays.stream(names)
                .filter(name -> {
                    String value = environment.getProperty(name);
                    return value == null || value.isBlank();
                })
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Production payment settings must be explicit: " + String.join(", ", missing));
        }
    }

    private boolean production() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> profile.equalsIgnoreCase("prod")
                        || profile.equalsIgnoreCase("production"));
    }
}
