package com.jobseekercopilot.paymentservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PromotionResponse {
    private String id;
    private boolean enabled;
    private Status status;
    private int bonusPercent;
    private int customerLimit;

    public enum Status {
        DISABLED,
        EXHAUSTED,
        AVAILABLE
    }
}
