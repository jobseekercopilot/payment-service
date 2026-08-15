package com.jobseekercopilot.paymentservice.systemdata;

import java.util.Map;

public record OwnerRuntimePaymentSummary(
        int legacyWallets,
        int documentCreditWallets,
        int ledgerEntries,
        int reservations,
        long balanceTokens,
        int balanceDocumentCredits,
        int orders,
        int providerEvents,
        int promotionReservations) {

    public int total() {
        return legacyWallets + documentCreditWallets + ledgerEntries
                + reservations + orders + providerEvents + promotionReservations;
    }

    public Map<String, Object> details(String scenarioId, String identityKey) {
        Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("scenarioId", scenarioId);
        details.put("identityKey", identityKey);
        details.put("walletExists", legacyWallets + documentCreditWallets > 0);
        details.put("wallets", legacyWallets + documentCreditWallets);
        details.put("legacyWallets", legacyWallets);
        details.put("documentCreditWallets", documentCreditWallets);
        details.put("balanceTokens", balanceTokens);
        details.put("balanceDocumentCredits", balanceDocumentCredits);
        details.put("ledgerEntries", ledgerEntries);
        details.put("reservations", reservations);
        details.put("orders", orders);
        details.put("providerEvents", providerEvents);
        details.put("promotionReservations", promotionReservations);
        return Map.copyOf(details);
    }
}
