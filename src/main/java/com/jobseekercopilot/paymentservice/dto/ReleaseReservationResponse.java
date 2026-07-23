package com.jobseekercopilot.paymentservice.dto;

import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReleaseReservationResponse {
    private UUID reservationId;
    private long releasedTokens;
    private WalletSummaryResponse wallet;
}
