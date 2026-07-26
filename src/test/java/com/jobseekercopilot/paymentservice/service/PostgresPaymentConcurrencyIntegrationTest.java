package com.jobseekercopilot.paymentservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobseekercopilot.paymentservice.dto.CommitReservationRequest;
import com.jobseekercopilot.paymentservice.dto.CreateReservationRequest;
import com.jobseekercopilot.paymentservice.dto.EstimateRequest;
import com.jobseekercopilot.paymentservice.dto.WalletSummaryResponse;
import com.jobseekercopilot.paymentservice.entity.AiTokenReservation;
import com.jobseekercopilot.paymentservice.entity.AiTokenTransaction;
import com.jobseekercopilot.paymentservice.entity.AiTokenWallet;
import com.jobseekercopilot.paymentservice.entity.ReservationStatus;
import com.jobseekercopilot.paymentservice.entity.TransactionType;
import com.jobseekercopilot.paymentservice.exception.BadRequestException;
import com.jobseekercopilot.paymentservice.exception.InsufficientTokensException;
import com.jobseekercopilot.paymentservice.repository.AiTokenReservationRepository;
import com.jobseekercopilot.paymentservice.repository.AiTokenTransactionRepository;
import com.jobseekercopilot.paymentservice.repository.AiTokenWalletRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class PostgresPaymentConcurrencyIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15.18-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add(
                "spring.flyway.locations",
                () -> "classpath:db/migration/common,classpath:db/migration/postgresql-live");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("payment.database.production-safety-check", () -> "false");
        registry.add("payment.ledger.verify-on-startup", () -> "true");
    }

    @Autowired private PaymentService paymentService;
    @Autowired private LedgerReconciliationService reconciliationService;
    @Autowired private AiTokenWalletRepository walletRepository;
    @Autowired private AiTokenTransactionRepository transactionRepository;
    @Autowired private AiTokenReservationRepository reservationRepository;

    @Test
    void concurrentWalletCreationGrantsStarterCreditExactlyOnce() throws Exception {
        String userId = uniqueUser("wallet-race");
        List<Callable<WalletSummaryResponse>> calls = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            calls.add(() -> paymentService.wallet(userId));
        }

        List<WalletSummaryResponse> responses = invokeTogether(calls);

        assertThat(responses)
                .hasSize(12)
                .allSatisfy(response -> {
                    assertThat(response.getUserId()).isEqualTo(userId);
                    assertThat(response.getBalanceTokens()).isEqualTo(20_000);
                });
        AiTokenWallet wallet = walletRepository.findByUserId(userId).orElseThrow();
        assertThat(transactionRepository.findByWalletIdOrderBySequenceNumberAsc(wallet.getId()))
                .extracting(AiTokenTransaction::getTransactionType)
                .containsExactly(TransactionType.FREE_TRIAL_GRANTED);
        assertThat(reconciliationService.reconcile(wallet.getId()).reconciled()).isTrue();
    }

    @Test
    void concurrentReservationsCannotOverspendWallet() throws Exception {
        String userId = uniqueUser("reservation-race");
        paymentService.wallet(userId);
        List<Callable<Throwable>> calls = List.of(
                reservationAttempt(userId, "one"),
                reservationAttempt(userId, "two"),
                reservationAttempt(userId, "three"));

        List<Throwable> outcomes = invokeTogether(calls);

        assertThat(outcomes).filteredOn(error -> error == null).hasSize(2);
        assertThat(outcomes)
                .filteredOn(InsufficientTokensException.class::isInstance)
                .hasSize(1);
        AiTokenWallet wallet = walletRepository.findByUserId(userId).orElseThrow();
        assertThat(wallet.getBalanceTokens()).isZero();
        assertThat(reservationRepository.findByUserId(userId))
                .hasSize(2)
                .allSatisfy(reservation ->
                        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RESERVED));
        assertThat(transactionRepository.findByWalletIdOrderBySequenceNumberAsc(wallet.getId()))
                .extracting(AiTokenTransaction::getBalanceDeltaTokens)
                .containsExactly(20_000L, -10_000L, -10_000L);
        assertThat(reconciliationService.reconcile(wallet.getId()).reconciled()).isTrue();
    }

    @Test
    void concurrentCreditsDoNotLoseUpdates() throws Exception {
        String userId = uniqueUser("credit-race");
        paymentService.wallet(userId);
        List<Callable<WalletSummaryResponse>> calls = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            calls.add(() -> paymentService.demoPurchase(userId, "starter").getWallet());
        }

        invokeTogether(calls);

        AiTokenWallet wallet = walletRepository.findByUserId(userId).orElseThrow();
        assertThat(wallet.getBalanceTokens()).isEqualTo(820_000);
        assertThat(wallet.getLifetimePurchasedTokens()).isEqualTo(800_000);
        assertThat(transactionRepository.findByWalletIdOrderBySequenceNumberAsc(wallet.getId()))
                .hasSize(9);
        assertThat(reconciliationService.reconcile(wallet.getId()).reconciled()).isTrue();
    }

    @Test
    void identicalConcurrentCommitsMutateLedgerOnceAndReplaySafely() throws Exception {
        String userId = uniqueUser("commit-retry");
        UUID reservationId = createReservation(userId);
        List<Callable<Throwable>> calls = List.of(
                commitAttempt(userId, reservationId, 7_300),
                commitAttempt(userId, reservationId, 7_300));

        List<Throwable> outcomes = invokeTogether(calls);

        assertThat(outcomes).containsOnlyNulls();
        AiTokenWallet wallet = walletRepository.findByUserId(userId).orElseThrow();
        AiTokenReservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.COMMITTED);
        assertThat(wallet.getBalanceTokens()).isEqualTo(12_700);
        assertThat(wallet.getLifetimeSpentTokens()).isEqualTo(7_300);
        assertThat(transactionRepository.findByWalletIdOrderBySequenceNumberAsc(wallet.getId()))
                .extracting(AiTokenTransaction::getTransactionType)
                .containsExactly(
                        TransactionType.FREE_TRIAL_GRANTED,
                        TransactionType.RESERVATION,
                        TransactionType.SPEND,
                        TransactionType.RESERVATION_RELEASED);
        assertThat(reconciliationService.reconcile(wallet.getId()).reconciled()).isTrue();
    }

    @Test
    void commitAndReleaseAreAtomicMutuallyExclusiveAndWinnerIsRetrySafe() throws Exception {
        String userId = uniqueUser("terminal-race");
        UUID reservationId = createReservation(userId);
        List<Callable<Throwable>> calls = List.of(
                commitAttempt(userId, reservationId, 7_300),
                releaseAttempt(userId, reservationId));

        List<Throwable> outcomes = invokeTogether(calls);

        assertThat(outcomes).filteredOn(error -> error == null).hasSize(1);
        assertThat(outcomes).filteredOn(BadRequestException.class::isInstance).hasSize(1);
        AiTokenReservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        AiTokenWallet wallet = walletRepository.findByUserId(userId).orElseThrow();
        int entryCount =
                transactionRepository.findByWalletIdOrderBySequenceNumberAsc(wallet.getId()).size();
        if (reservation.getStatus() == ReservationStatus.COMMITTED) {
            paymentService.commitReservation(userId, reservationId, commitRequest(7_300));
            assertThatThrownBy(() -> paymentService.releaseReservation(userId, reservationId, "retry"))
                    .isInstanceOf(BadRequestException.class);
            assertThat(walletRepository.findByUserId(userId).orElseThrow().getBalanceTokens())
                    .isEqualTo(12_700);
        } else {
            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
            paymentService.releaseReservation(userId, reservationId, "retry");
            assertThatThrownBy(() -> paymentService.commitReservation(
                            userId, reservationId, commitRequest(7_300)))
                    .isInstanceOf(BadRequestException.class);
            assertThat(walletRepository.findByUserId(userId).orElseThrow().getBalanceTokens())
                    .isEqualTo(20_000);
        }
        assertThat(transactionRepository.findByWalletIdOrderBySequenceNumberAsc(wallet.getId()))
                .hasSize(entryCount);
        assertThat(reconciliationService.reconcile(wallet.getId()).reconciled()).isTrue();
    }

    @Test
    void invalidAndOverflowingAmountsFailWithoutLedgerMutation() {
        String userId = uniqueUser("range-check");
        paymentService.wallet(userId);
        AiTokenWallet wallet = walletRepository.findByUserId(userId).orElseThrow();
        int entryCount =
                transactionRepository.findByWalletIdOrderBySequenceNumberAsc(wallet.getId()).size();

        CreateReservationRequest invalid = new CreateReservationRequest();
        invalid.setFeature("INVALID");
        invalid.setEstimatedTokens(0);
        assertThatThrownBy(() -> paymentService.createReservation(userId, invalid))
                .isInstanceOf(BadRequestException.class);

        EstimateRequest negativeEstimate = new EstimateRequest();
        negativeEstimate.setFeature("INVALID");
        negativeEstimate.setEstimatedInputTokens(-1);
        assertThatThrownBy(() -> paymentService.estimate(userId, negativeEstimate))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("AI Credit amount must not be negative");

        wallet.setBalanceTokens(Long.MAX_VALUE);
        walletRepository.saveAndFlush(wallet);
        try {
            assertThatThrownBy(() -> paymentService.demoPurchase(userId, "starter"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("AI Credit amount exceeds the supported range");
            assertThat(transactionRepository.findByWalletIdOrderBySequenceNumberAsc(wallet.getId()))
                    .hasSize(entryCount);
        } finally {
            AiTokenWallet restore = walletRepository.findById(wallet.getId()).orElseThrow();
            restore.setBalanceTokens(20_000);
            walletRepository.saveAndFlush(restore);
        }
        assertThat(reconciliationService.reconcile(wallet.getId()).reconciled()).isTrue();
    }

    private Callable<Throwable> reservationAttempt(String userId, String referenceId) {
        return () -> {
            try {
                CreateReservationRequest request = new CreateReservationRequest();
                request.setFeature("CV_AND_COVER_LETTER_GENERATION");
                request.setEstimatedTokens(10_000);
                request.setReferenceType("CONCURRENCY_TEST");
                request.setReferenceId(referenceId);
                paymentService.createReservation(userId, request);
                return null;
            } catch (Throwable error) {
                return error;
            }
        };
    }

    private Callable<Throwable> commitAttempt(String userId, UUID reservationId, long actualTokens) {
        return () -> {
            try {
                paymentService.commitReservation(userId, reservationId, commitRequest(actualTokens));
                return null;
            } catch (Throwable error) {
                return error;
            }
        };
    }

    private Callable<Throwable> releaseAttempt(String userId, UUID reservationId) {
        return () -> {
            try {
                paymentService.releaseReservation(userId, reservationId, "concurrent release");
                return null;
            } catch (Throwable error) {
                return error;
            }
        };
    }

    private UUID createReservation(String userId) {
        CreateReservationRequest request = new CreateReservationRequest();
        request.setFeature("CV_AND_COVER_LETTER_GENERATION");
        request.setEstimatedTokens(10_000);
        return paymentService.createReservation(userId, request).getReservationId();
    }

    private CommitReservationRequest commitRequest(long actualTokens) {
        CommitReservationRequest request = new CommitReservationRequest();
        request.setActualTokens(actualTokens);
        return request;
    }

    private <T> List<T> invokeTogether(List<Callable<T>> calls) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(calls.size());
        CountDownLatch ready = new CountDownLatch(calls.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = calls.stream()
                    .map(call -> executor.submit(() -> {
                        ready.countDown();
                        if (!start.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Concurrent test start timed out");
                        }
                        return call.call();
                    }))
                    .toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                try {
                    results.add(future.get(30, TimeUnit.SECONDS));
                } catch (ExecutionException exception) {
                    if (exception.getCause() instanceof Exception cause) {
                        throw cause;
                    }
                    throw exception;
                }
            }
            return results;
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private String uniqueUser(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
