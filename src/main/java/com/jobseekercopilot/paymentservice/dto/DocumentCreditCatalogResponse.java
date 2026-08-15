package com.jobseekercopilot.paymentservice.dto;

import com.jobseekercopilot.paymentservice.config.PaymentProperties;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DocumentCreditCatalogResponse {
    private String catalogVersion;
    private String currency;
    private String billingCountry;
    private PaymentProperties.TaxTreatment taxTreatment;
    private PaymentProperties.TaxStatus taxStatus;
    private boolean displayedPriceIsCheckoutTotal;
    private boolean automaticRenewal;
    private String creditUnit;
    private int freeAllowanceCredits;
    private List<DocumentCreditPlanResponse> plans;
    private PromotionResponse promotion;
}
