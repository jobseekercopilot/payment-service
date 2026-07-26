package com.jobseekercopilot.paymentservice.systemdata;

import com.jobseekercopilot.paymentservice.entity.AiTokenReservation;
import com.jobseekercopilot.paymentservice.entity.AiTokenTransaction;
import com.jobseekercopilot.paymentservice.entity.AiTokenWallet;
import com.jobseekercopilot.paymentservice.exception.BadRequestException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class FixtureLedgerSeedValidator {
    public void validate(SystemDataPaymentSeedRequest request) {
        if (request == null || request.wallet() == null) {
            throw invalid("A fixture wallet is required");
        }

        AiTokenWallet wallet = request.wallet();
        if (wallet.getId() == null
                || request.userId() == null
                || !request.userId().equals(wallet.getUserId())
                || wallet.getBalanceTokens() < 0) {
            throw invalid("Fixture wallet identity or balance is invalid");
        }

        List<AiTokenTransaction> entries =
                request.transactions() == null ? List.of() : request.transactions();
        long expectedBalance = 0;
        Instant previousTimestamp = null;
        for (AiTokenTransaction entry : entries) {
            if (entry == null
                    || !request.userId().equals(entry.getUserId())
                    || !wallet.getId().equals(entry.getWalletId())
                    || entry.getTransactionType() == null
                    || entry.getTokenAmount() < 0
                    || entry.getBalanceBefore() < 0
                    || entry.getBalanceAfter() < 0
                    || entry.getOperationId() == null
                    || entry.getOperationId().isBlank()
                    || entry.getCreatedAt() == null) {
                throw invalid("Fixture ledger entry identity or required fields are invalid");
            }

            long calculatedAfter;
            try {
                calculatedAfter =
                        Math.addExact(entry.getBalanceBefore(), entry.getBalanceDeltaTokens());
            } catch (ArithmeticException exception) {
                throw invalid("Fixture ledger entry balance arithmetic overflowed");
            }
            if (entry.getBalanceBefore() != expectedBalance
                    || entry.getBalanceAfter() != calculatedAfter
                    || (previousTimestamp != null
                            && entry.getCreatedAt().isBefore(previousTimestamp))) {
                throw invalid("Fixture ledger entries do not form one chronological balance chain");
            }
            expectedBalance = entry.getBalanceAfter();
            previousTimestamp = entry.getCreatedAt();
        }
        if (expectedBalance != wallet.getBalanceTokens()) {
            throw invalid("Fixture wallet balance does not reconcile with its ledger");
        }

        List<AiTokenReservation> reservations =
                request.reservations() == null ? List.of() : request.reservations();
        boolean invalidReservation = reservations.stream()
                .filter(Objects::nonNull)
                .anyMatch(reservation -> !request.userId().equals(reservation.getUserId())
                        || !wallet.getId().equals(reservation.getWalletId()));
        if (reservations.stream().anyMatch(Objects::isNull) || invalidReservation) {
            throw invalid("Fixture reservations must belong to the fixture wallet");
        }
    }

    private BadRequestException invalid(String reason) {
        return new BadRequestException("Invalid isolated payment fixture: " + reason);
    }
}
