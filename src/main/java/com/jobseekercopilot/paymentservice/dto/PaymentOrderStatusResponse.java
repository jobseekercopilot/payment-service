package com.jobseekercopilot.paymentservice.dto;

import com.jobseekercopilot.paymentservice.config.PaymentProperties;
import com.jobseekercopilot.paymentservice.entity.PaymentOrderStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentOrderStatusResponse {
    private UUID orderId;
    private PaymentOrderStatus status;
    private String pricingPlanId;
    private int documentCredits;
    private int promotionBonusDocumentCredits;
    private int totalGrantedDocumentCredits;
    private long priceMinor;
    private String currency;
    private PaymentProperties.TaxTreatment taxTreatment;
    private PaymentProperties.TaxStatus taxStatus;
    private PaymentProperties.LegalEntityType legalEntityType;
    private String legalEntityConfigurationVersion;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant fulfilledAt;
    private boolean creditsAdded;
    private MessageCode messageCode;

    public enum MessageCode {
        PAYMENT_PENDING,
        CREDITS_ADDED,
        CHECKOUT_EXPIRED,
        CHECKOUT_CANCELLED,
        PAYMENT_REFUNDED,
        PAYMENT_PARTIALLY_REFUNDED,
        PAYMENT_DISPUTED,
        PAYMENT_REVIEW_REQUIRED
    }
}
