package com.jobseekercopilot.paymentservice.dto;

import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DocumentCreditReleaseResponse {
    private UUID reservationId;
    private int releasedDocumentCredits;
    private String status;
    private DocumentCreditWalletResponse wallet;
}
