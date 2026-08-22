package com.jobseekercopilot.paymentservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DocumentCreditPlanResponse {
    private String id;
    private String name;
    private String description;
    private int documentCredits;
    private long priceMinor;
    private String currency;
    private int fullApplicationEquivalent;
    private int promotionBonusDocumentCredits;
    private boolean active;
    private int sortOrder;
}
