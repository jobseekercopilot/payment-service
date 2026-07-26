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
    private List<PricingPlan> pricingPlans = new ArrayList<>();
    private Database database = new Database();
    private Ledger ledger = new Ledger();
    private ReservationRecovery reservationRecovery = new ReservationRecovery();

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
    public static class PricingPlan {
        private String id;
        private String name;
        private String description;
        private long tokenAmount;
        private long priceGbpPence;
        private boolean active;
        private int sortOrder;
    }
}
