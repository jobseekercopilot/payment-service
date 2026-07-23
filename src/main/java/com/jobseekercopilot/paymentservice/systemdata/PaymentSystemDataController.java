package com.jobseekercopilot.paymentservice.systemdata;

import com.jobseekercopilot.paymentservice.repository.AiTokenReservationRepository;
import com.jobseekercopilot.paymentservice.repository.AiTokenTransactionRepository;
import com.jobseekercopilot.paymentservice.repository.AiTokenWalletRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/system-data")
public class PaymentSystemDataController {
    private final EnvironmentDataGuard guard;
    private final AiTokenWalletRepository walletRepository;
    private final AiTokenTransactionRepository transactionRepository;
    private final AiTokenReservationRepository reservationRepository;

    public PaymentSystemDataController(
            EnvironmentDataGuard guard,
            AiTokenWalletRepository walletRepository,
            AiTokenTransactionRepository transactionRepository,
            AiTokenReservationRepository reservationRepository) {
        this.guard = guard;
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.reservationRepository = reservationRepository;
    }

    @PostMapping("/seed/payments")
    public ResponseEntity<SystemDataResult> seedPayments(@RequestBody SystemDataPaymentSeedRequest request) {
        guard.requireEnabled();
        var wallet = walletRepository.save(request.wallet());
        var transactions = transactionRepository.saveAll(request.transactions() == null ? List.of() : request.transactions());
        var reservations = reservationRepository.saveAll(request.reservations() == null ? List.of() : request.reservations());
        return ResponseEntity.ok(SystemDataResult.success("SEED", 1 + transactions.size() + reservations.size(), guard.activeEnvironment(), Map.of(
                "userId", wallet.getUserId(),
                "balanceTokens", wallet.getBalanceTokens(),
                "ledgerEntries", transactions.size(),
                "reservations", reservations.size())));
    }

    @Transactional
    @DeleteMapping("/scenario/{scenarioId}/payments/{userId}")
    public ResponseEntity<SystemDataResult> resetPayments(@PathVariable String scenarioId, @PathVariable String userId) {
        guard.requireEnabled();
        int transactions = transactionRepository.findByUserId(userId).size();
        int reservations = reservationRepository.findByUserId(userId).size();
        int wallets = walletRepository.findByUserId(userId).isPresent() ? 1 : 0;
        reservationRepository.deleteByUserId(userId);
        transactionRepository.deleteByUserId(userId);
        walletRepository.deleteByUserId(userId);
        return ResponseEntity.ok(SystemDataResult.success("RESET", wallets + transactions + reservations, guard.activeEnvironment(), Map.of(
                "scenarioId", scenarioId,
                "userId", userId,
                "wallets", wallets,
                "ledgerEntries", transactions,
                "reservations", reservations)));
    }

    @GetMapping("/verify/payments/{userId}")
    public ResponseEntity<SystemDataResult> verifyPayments(@PathVariable String userId) {
        guard.requireEnabled();
        int transactions = transactionRepository.findByUserId(userId).size();
        int reservations = reservationRepository.findByUserId(userId).size();
        long balance = walletRepository.findByUserId(userId).map(wallet -> wallet.getBalanceTokens()).orElse(0L);
        return ResponseEntity.ok(SystemDataResult.success("VERIFY", transactions + reservations + (balance > 0 ? 1 : 0), guard.activeEnvironment(), Map.of(
                "userId", userId,
                "walletExists", walletRepository.findByUserId(userId).isPresent(),
                "balanceTokens", balance,
                "ledgerEntries", transactions,
                "reservations", reservations)));
    }
}
