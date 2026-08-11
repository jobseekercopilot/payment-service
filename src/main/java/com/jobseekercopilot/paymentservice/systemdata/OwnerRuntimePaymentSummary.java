package com.jobseekercopilot.paymentservice.systemdata;

import java.util.Map;

public record OwnerRuntimePaymentSummary(
        int wallets,
        int ledgerEntries,
        int reservations,
        long balanceTokens) {

    public int total() {
        return wallets + ledgerEntries + reservations;
    }

    public Map<String, Object> details(String scenarioId, String identityKey) {
        return Map.of(
                "scenarioId", scenarioId,
                "identityKey", identityKey,
                "walletExists", wallets == 1,
                "wallets", wallets,
                "balanceTokens", balanceTokens,
                "ledgerEntries", ledgerEntries,
                "reservations", reservations);
    }
}
