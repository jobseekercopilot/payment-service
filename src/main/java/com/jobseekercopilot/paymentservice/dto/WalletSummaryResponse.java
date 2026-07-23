package com.jobseekercopilot.paymentservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WalletSummaryResponse {
    private String userId;
    private long balanceTokens;
    private long lifetimePurchasedTokens;
    private long lifetimeSpentTokens;
    private long lifetimeRefundedTokens;
    private boolean freeTrialGranted;
}
