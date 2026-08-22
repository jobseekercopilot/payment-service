package com.jobseekercopilot.paymentservice.dto;

import com.jobseekercopilot.paymentservice.config.PaymentProperties;
import com.jobseekercopilot.paymentservice.entity.PaymentOrderStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentOrderResponse {
    private UUID orderId;
    private PaymentOrderStatus status;
    private String ownerId;
    private String catalogVersion;
    private String pricingPlanId;
    private String pricingPlanName;
    private int documentCredits;
    private int promotionBonusDocumentCredits;
    private boolean promotionGuaranteed;
    private long priceMinor;
    private String currency;
    private String billingCountry;
    private PaymentProperties.TaxTreatment taxTreatment;
    private PaymentProperties.TaxStatus taxStatus;
    private PaymentProperties.LegalEntityType legalEntityType;
    private String legalEntityConfigurationVersion;
    private boolean displayedPriceIsCheckoutTotal;
    private String consumerTermsVersion;
    private boolean consumerAcknowledgementsRecorded;
    private Instant consumerTermsAcceptedAt;
    private Instant expiresAt;
    private String stripeSessionId;
}
