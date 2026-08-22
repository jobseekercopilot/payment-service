package com.jobseekercopilot.paymentservice.dto;

import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProviderPaymentEventResponse {
    private String providerEventId;
    private String outcome;
    private UUID orderId;
    private String orderStatus;
    private int grantedDocumentCredits;
    private int reversedDocumentCredits;
    private int reviewShortfallDocumentCredits;
}
