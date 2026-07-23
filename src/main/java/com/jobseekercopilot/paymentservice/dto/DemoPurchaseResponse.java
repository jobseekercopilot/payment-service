package com.jobseekercopilot.paymentservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DemoPurchaseResponse {
    private WalletSummaryResponse wallet;
    private TransactionResponse transaction;
}
