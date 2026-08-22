package com.jobseekercopilot.paymentservice.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "payment")
@Data
public class PaymentProperties {
    private long freeStarterTokens = 20000;
    private int freeDocumentCredits = 2;
    private String catalogVersion = "public-beta-2026-08-22";
    private String currency = "GBP";
    private String billingCountry = "GB";
    private TaxTreatment taxTreatment = TaxTreatment.VAT_NOT_CHARGED;
    private TaxStatus taxStatus = TaxStatus.NOT_CONFIGURED;
    private boolean demoPurchaseEnabled;
    private boolean legacyStripeConfirmationEnabled;
    private List<PricingPlan> pricingPlans = new ArrayList<>();
    private Database database = new Database();
    private Ledger ledger = new Ledger();
    private ReservationRecovery reservationRecovery = new ReservationRecovery();
    private Promotion promotion = new Promotion();
    private Checkout checkout = new Checkout();
    private ConsumerTerms consumerTerms = new ConsumerTerms();
    private LegalEntity legalEntity = new LegalEntity();
    private Retention retention = new Retention();
    private AccountLifecycle accountLifecycle = new AccountLifecycle();

    public List<PricingPlan> activePricingPlans() {
        return pricingPlans.stream()
                .filter(PricingPlan::isActive)
                .sorted(Comparator.comparingInt(PricingPlan::getSortOrder))
                .toList();
    }

    public Optional<PricingPlan> activePricingPlan(String id) {
        return activePricingPlans().stream()
                .filter(plan -> plan.getId().equals(id))
                .findFirst();
    }

    public Optional<PricingPlan> legacyActivePricingPlan(String id) {
        return activePricingPlan("standard".equals(id) ? "active" : id);
    }

    public enum TaxTreatment {
        VAT_NOT_CHARGED,
        VAT_INCLUDED
    }

    public enum TaxStatus {
        NOT_CONFIGURED,
        NOT_VAT_REGISTERED,
        VAT_REGISTERED
    }

    public enum LegalEntityType {
        NOT_CONFIGURED,
        SOLE_TRADER,
        LIMITED_COMPANY
    }

    @Data
    public static class Database {
        private boolean productionSafetyCheck = true;
    }

    @Data
    public static class Ledger {
        private boolean verifyOnStartup = true;
    }

    @Data
    public static class ReservationRecovery {
        private boolean enabled = true;
        private Duration ttl = Duration.ofMinutes(15);
        private Duration interval = Duration.ofSeconds(30);
        private int batchSize = 100;
    }

    @Data
    public static class Promotion {
        private String id = "founding-200";
        private boolean enabled = true;
        private boolean releaseAuthorised;
        private int customerLimit = 200;
        private int bonusPercent = 50;
    }

    @Data
    public static class Checkout {
        private boolean enabled;
        private boolean releaseAuthorised;
        private boolean providerLiveModeExpected;
        private Duration orderTtl = Duration.ofHours(1);
    }

    @Data
    public static class ConsumerTerms {
        private String version = "uk-consumer-terms-2026-08-15";
    }

    @Data
    public static class LegalEntity {
        private LegalEntityType type = LegalEntityType.NOT_CONFIGURED;
        private String configurationVersion;
        private boolean reviewed;
    }

    @Data
    public static class Retention {
        private int financialRecordYears = 7;
    }

    @Data
    public static class AccountLifecycle {
        private String stripeGatewayUrl = "http://localhost:8100";
        private String stripeGatewayToken;
        private boolean providerSessionRecoveryEnabled = true;
        private Duration providerSessionRecoveryInterval = Duration.ofSeconds(30);
        private int providerSessionRecoveryBatchSize = 50;
    }

    @Data
    public static class PricingPlan {
        private String id;
        private String name;
        private String description;
        private long tokenAmount;
        private int documentCredits;
        private long priceGbpPence;
        private boolean active;
        private int sortOrder;
    }
}
