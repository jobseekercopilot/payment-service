package com.jobseekercopilot.paymentservice.systemdata;

import com.jobseekercopilot.paymentservice.entity.AiTokenReservation;
import com.jobseekercopilot.paymentservice.entity.AiTokenTransaction;
import com.jobseekercopilot.paymentservice.repository.AiTokenReservationRepository;
import com.jobseekercopilot.paymentservice.repository.AiTokenTransactionRepository;
import com.jobseekercopilot.paymentservice.repository.AiTokenWalletRepository;
import com.jobseekercopilot.paymentservice.service.LedgerReconciliationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/system-data")
@SecurityRequirement(name = "environmentDataToken")
@Validated
public class PaymentSystemDataController {
    private final EnvironmentDataGuard guard;
    private final AiTokenWalletRepository walletRepository;
    private final AiTokenTransactionRepository transactionRepository;
    private final AiTokenReservationRepository reservationRepository;
    private final FixtureLedgerSeedValidator fixtureLedgerSeedValidator;
    private final LedgerReconciliationService reconciliationService;
    private final EnvironmentLedgerReset environmentLedgerReset;

    public PaymentSystemDataController(
            EnvironmentDataGuard guard,
            AiTokenWalletRepository walletRepository,
            AiTokenTransactionRepository transactionRepository,
            AiTokenReservationRepository reservationRepository,
            FixtureLedgerSeedValidator fixtureLedgerSeedValidator,
            LedgerReconciliationService reconciliationService,
            EnvironmentLedgerReset environmentLedgerReset) {
        this.guard = guard;
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.reservationRepository = reservationRepository;
        this.fixtureLedgerSeedValidator = fixtureLedgerSeedValidator;
        this.reconciliationService = reconciliationService;
        this.environmentLedgerReset = environmentLedgerReset;
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
        return resetPaymentsForOwner(scenarioId, userId, Map.of());
    }

    private ResponseEntity<SystemDataResult> resetPaymentsForOwner(
            String scenarioId, String userId, Map<String, Object> extraDetails) {
        int reservations = reservationRepository.findByUserId(userId).size();
        int wallets = walletRepository.findByUserId(userId).isPresent() ? 1 : 0;
        reservationRepository.deleteByUserId(userId);
        int transactions = environmentLedgerReset.deleteOwnerLedger(userId);
        walletRepository.deleteByUserId(userId);
        Map<String, Object> details = new java.util.LinkedHashMap<>(extraDetails);
        details.put("scenarioId", scenarioId);
        details.put("userId", userId);
        details.put("wallets", wallets);
        details.put("ledgerEntries", transactions);
        details.put("reservations", reservations);
        return ResponseEntity.ok(SystemDataResult.success(
                "RESET", wallets + transactions + reservations, guard.activeEnvironment(), details));
    }

    @GetMapping("/verify/payments/{userId}")
    public ResponseEntity<SystemDataResult> verifyPayments(@PathVariable String userId) {
        guard.requireEnabled();
        return verifyPaymentsForOwner(userId, Map.of());
    }

    private ResponseEntity<SystemDataResult> verifyPaymentsForOwner(
            String userId, Map<String, Object> extraDetails) {
        int transactions = transactionRepository.findByUserId(userId).size();
        int reservations = reservationRepository.findByUserId(userId).size();
        long balance = walletRepository.findByUserId(userId).map(wallet -> wallet.getBalanceTokens()).orElse(0L);
        Map<String, Object> details = new java.util.LinkedHashMap<>(extraDetails);
        details.put("userId", userId);
        details.put("walletExists", walletRepository.findByUserId(userId).isPresent());
        details.put("balanceTokens", balance);
        details.put("ledgerEntries", transactions);
        details.put("reservations", reservations);
        return ResponseEntity.ok(SystemDataResult.success(
                "VERIFY", transactions + reservations + (balance > 0 ? 1 : 0),
                guard.activeEnvironment(), details));
    }

    @Transactional
    @DeleteMapping(
            "/v1/runtime-owners/{scenarioId}/identities/{identityKey}/owners/{userId}")
    public ResponseEntity<SystemDataResult> resetRuntimeOwner(
            @PathVariable
            @Pattern(regexp = "[a-z0-9][a-z0-9-]{1,54}-v[1-9][0-9]{0,6}")
            String scenarioId,
            @PathVariable
            @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,54}")
            String identityKey,
            @PathVariable UUID userId) {
        guard.requireRuntimeOwnerCleanup();
        SyntheticOwnerId.requireMatches(scenarioId, identityKey, userId);
        String owner = userId.toString();
        OwnerRuntimePaymentSummary summary = ownerSummary(owner);
        reservationRepository.deleteByUserId(owner);
        environmentLedgerReset.deleteOwnerLedger(owner);
        walletRepository.deleteByUserId(owner);
        return ResponseEntity.ok(SystemDataResult.success(
                "RESET_RUNTIME_OWNER",
                summary.total(),
                guard.activeEnvironment(),
                summary.details(scenarioId, identityKey)));
    }

    @GetMapping(
            "/v1/runtime-owners/{scenarioId}/identities/{identityKey}/owners/{userId}")
    public ResponseEntity<SystemDataResult> verifyRuntimeOwner(
            @PathVariable
            @Pattern(regexp = "[a-z0-9][a-z0-9-]{1,54}-v[1-9][0-9]{0,6}")
            String scenarioId,
            @PathVariable
            @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,54}")
            String identityKey,
            @PathVariable UUID userId) {
        guard.requireRuntimeOwnerCleanup();
        SyntheticOwnerId.requireMatches(scenarioId, identityKey, userId);
        OwnerRuntimePaymentSummary summary = ownerSummary(userId.toString());
        return ResponseEntity.ok(SystemDataResult.success(
                "VERIFY_RUNTIME_OWNER",
                summary.total(),
                guard.activeEnvironment(),
                summary.details(scenarioId, identityKey)));
    }

    private OwnerRuntimePaymentSummary ownerSummary(String ownerId) {
        var wallet = walletRepository.findByUserId(ownerId);
        return new OwnerRuntimePaymentSummary(
                wallet.isPresent() ? 1 : 0,
                transactionRepository.findByUserId(ownerId).size(),
                reservationRepository.findByUserId(ownerId).size(),
                wallet.map(value -> value.getBalanceTokens()).orElse(0L));
    }
}
