package com.jobseekercopilot.paymentservice.systemdata;

import com.jobseekercopilot.paymentservice.entity.AiTokenReservation;
import com.jobseekercopilot.paymentservice.entity.AiTokenTransaction;
import com.jobseekercopilot.paymentservice.entity.AiTokenWallet;

import java.util.List;

public record SystemDataPaymentSeedRequest(
        String scenarioId,
        String userId,
        AiTokenWallet wallet,
        List<AiTokenTransaction> transactions,
        List<AiTokenReservation> reservations) {
}
