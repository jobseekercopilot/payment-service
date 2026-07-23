package com.jobseekercopilot.paymentservice.dto;

import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CommitReservationResponse {
    private UUID reservationId;
    private long committedTokens;
    private long releasedTokens;
    private WalletSummaryResponse wallet;
    private TransactionResponse spendTransaction;
}
