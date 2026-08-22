package com.jobseekercopilot.paymentservice.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AccountPaymentExportResponse(
        String schemaVersion,
        String ownerId,
        Instant generatedAt,
        int financialRecordRetentionYears,
        WalletRecord wallet,
        List<TransactionRecord> transactions,
        List<ReservationRecord> reservations,
        List<OrderRecord> orders,
        List<ProviderEventRecord> providerEvents) {

    public record WalletRecord(
            int balanceDocumentCredits,
            int lifetimePurchasedDocumentCredits,
            int lifetimeSpentDocumentCredits,
            int lifetimeReversedDocumentCredits,
            int reviewDebtDocumentCredits,
            String status,
            Instant createdAt,
            Instant revokedAt) {}

    public record TransactionRecord(
            UUID id,
            long sequence,
            String type,
            int documentCredits,
            int balanceBeforeDocumentCredits,
            int balanceAfterDocumentCredits,
            String operationId,
            String referenceType,
            String referenceId,
            Instant createdAt) {}

    public record ReservationRecord(
            UUID id,
            String operationKey,
            int documentCredits,
            String status,
            boolean regeneration,
            String referenceType,
            String referenceId,
            Instant createdAt,
            Instant expiresAt,
            Instant committedAt,
            Instant releasedAt) {}

    public record OrderRecord(
            UUID id,
            String status,
            String catalogVersion,
            String pricingPlanId,
            int baseDocumentCredits,
            int promotionBonusDocumentCredits,
            int reversedDocumentCredits,
            int reviewShortfallDocumentCredits,
            long priceMinor,
            String currency,
            String taxTreatment,
            String taxStatus,
            String legalEntityType,
            String legalEntityConfigurationVersion,
            String consumerTermsVersion,
            boolean consumerAcknowledgementsRecorded,
            Instant consumerTermsAcceptedAt,
            String providerCheckoutSessionId,
            String providerPaymentIntentId,
            Instant createdAt,
            Instant paidAt,
            Instant fulfilledAt) {}

    public record ProviderEventRecord(
            UUID id,
            String provider,
            String providerEventId,
            String eventType,
            UUID orderId,
            String payloadSha256,
            String providerObjectId,
            Long amountMinor,
            String currency,
            String billingCountry,
            Boolean providerLivemode,
            Instant providerCreatedAt,
            String outcome,
            Instant receivedAt,
            Instant processedAt) {}
}
