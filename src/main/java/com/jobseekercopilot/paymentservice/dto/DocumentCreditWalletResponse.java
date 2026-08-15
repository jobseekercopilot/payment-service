package com.jobseekercopilot.paymentservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DocumentCreditWalletResponse {
    private int balanceDocumentCredits;
    private int lifetimePurchasedDocumentCredits;
    private int lifetimeSpentDocumentCredits;
    private int lifetimeReversedDocumentCredits;
    private int reviewDebtDocumentCredits;
    private boolean freeAllowanceGranted;
    private String status;
}
