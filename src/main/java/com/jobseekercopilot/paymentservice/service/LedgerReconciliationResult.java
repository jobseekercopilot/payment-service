package com.jobseekercopilot.paymentservice.service;

import java.util.UUID;

public record LedgerReconciliationResult(
        UUID walletId,
        long storedBalanceTokens,
        long reconstructedBalanceTokens,
        int ledgerEntryCount,
        boolean balanceChainValid) {

    public boolean reconciled() {
        return balanceChainValid && storedBalanceTokens == reconstructedBalanceTokens;
    }
}
