package com.jobseekercopilot.paymentservice.systemdata;

import com.jobseekercopilot.paymentservice.entity.AiTokenReservation;
import com.jobseekercopilot.paymentservice.entity.AiTokenTransaction;
import com.jobseekercopilot.paymentservice.repository.AiTokenReservationRepository;
import com.jobseekercopilot.paymentservice.repository.AiTokenTransactionRepository;
import com.jobseekercopilot.paymentservice.repository.AiTokenWalletRepository;
import com.jobseekercopilot.paymentservice.service.LedgerReconciliationService;
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
    private final FixtureLedgerSeedValidator fixtureLedgerSeedValidator;
    private final LedgerReconciliationService reconciliationService;

    public PaymentSystemDataController(
            EnvironmentDataGuard guard,
            AiTokenWalletRepository walletRepository,
            AiTokenTransactionRepository transactionRepository,
            AiTokenReservationRepository reservationRepository,
            FixtureLedgerSeedValidator fixtureLedgerSeedValidator,
            LedgerReconciliationService reconciliationService) {
        this.guard = guard;
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.reservationRepository = reservationRepository;
        this.fixtureLedgerSeedValidator = fixtureLedgerSeedValidator;
        this.reconciliationService = reconciliationService;
    }

    @Transactional
    @PostMapping("/seed/payments")
    public ResponseEntity<SystemDataResult> seedPayments(@RequestBody SystemDataPaymentSeedRequest request) {
        guard.requireEnabled();
        fixtureLedgerSeedValidator.validate(request);
        var wallet = walletRepository.saveAndFlush(request.wallet());
        var transactionsToSave =
                request.transactions() == null ? List.<AiTokenTransaction>of() : request.transactions();
        transactionsToSave.forEach(transaction -> transaction.setWalletId(wallet.getId()));
        var reservationsToSave =
                request.reservations() == null ? List.<AiTokenReservation>of() : request.reservations();
        reservationsToSave.forEach(reservation -> reservation.setWalletId(wallet.getId()));
        var transactions = transactionRepository.saveAllAndFlush(transactionsToSave);
        var reservations = reservationRepository.saveAllAndFlush(reservationsToSave);
        if (!reconciliationService.reconcile(wallet.getId()).reconciled()) {
            throw new IllegalStateException("Isolated payment fixture failed ledger reconciliation");
        }
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
