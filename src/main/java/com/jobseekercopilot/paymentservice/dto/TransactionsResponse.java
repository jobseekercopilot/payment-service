package com.jobseekercopilot.paymentservice.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransactionsResponse {
    private String userId;
    private List<TransactionResponse> transactions;
}
