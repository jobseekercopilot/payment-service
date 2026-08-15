package com.jobseekercopilot.paymentservice.dto;

import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DocumentCreditCommitResponse {
    private UUID reservationId;
    private int spentDocumentCredits;
    private String status;
    private DocumentCreditWalletResponse wallet;
}
