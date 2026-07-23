package com.jobseekercopilot.paymentservice.controller;

import com.jobseekercopilot.paymentservice.dto.CommitReservationRequest;
import com.jobseekercopilot.paymentservice.dto.CommitReservationResponse;
import com.jobseekercopilot.paymentservice.dto.ConfirmStripePurchaseRequest;
import com.jobseekercopilot.paymentservice.dto.CreateReservationRequest;
import com.jobseekercopilot.paymentservice.dto.DemoPurchaseRequest;
import com.jobseekercopilot.paymentservice.dto.DemoPurchaseResponse;
import com.jobseekercopilot.paymentservice.dto.EstimateRequest;
import com.jobseekercopilot.paymentservice.dto.EstimateResponse;
import com.jobseekercopilot.paymentservice.dto.PricingPlansResponse;
import com.jobseekercopilot.paymentservice.dto.ReleaseReservationRequest;
import com.jobseekercopilot.paymentservice.dto.ReleaseReservationResponse;
import com.jobseekercopilot.paymentservice.dto.ReservationResponse;
import com.jobseekercopilot.paymentservice.dto.TransactionsResponse;
import com.jobseekercopilot.paymentservice.dto.WalletSummaryResponse;
import com.jobseekercopilot.paymentservice.exception.MissingUserIdException;
import com.jobseekercopilot.paymentservice.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {
    private static final String USER_ID_HEADER = "X-User-Id";
    private final PaymentService paymentService;

    @GetMapping("/wallet")
    @Operation(summary = "Get or create the user's AI token wallet")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Wallet summary returned"),
            @ApiResponse(responseCode = "401", description = "Missing X-User-Id header")
    })
    public ResponseEntity<WalletSummaryResponse> wallet(
            @Parameter(in = ParameterIn.HEADER, name = USER_ID_HEADER, required = true)
            @RequestHeader(name = USER_ID_HEADER, required = false) String userId) {
        return ResponseEntity.ok(paymentService.wallet(requireUserId(userId)));
    }

    @GetMapping("/transactions")
    @Operation(summary = "Get the user's AI token transaction history")
    public ResponseEntity<TransactionsResponse> transactions(
            @Parameter(in = ParameterIn.HEADER, name = USER_ID_HEADER, required = true)
            @RequestHeader(name = USER_ID_HEADER, required = false) String userId,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return ResponseEntity.ok(paymentService.transactions(requireUserId(userId), limit));
    }

    @GetMapping("/pricing")
    @Operation(summary = "Get active AI token pricing plans")
    public ResponseEntity<PricingPlansResponse> pricing() {
        return ResponseEntity.ok(paymentService.pricing());
    }

    @PostMapping("/demo-purchase")
    @Operation(summary = "Demo purchase AI tokens")
    public ResponseEntity<DemoPurchaseResponse> demoPurchase(
            @Parameter(in = ParameterIn.HEADER, name = USER_ID_HEADER, required = true)
            @RequestHeader(name = USER_ID_HEADER, required = false) String userId,
            @Valid @RequestBody DemoPurchaseRequest request) {
        return ResponseEntity.ok(paymentService.demoPurchase(requireUserId(userId), request.getPricingPlanId()));
    }

    @PostMapping("/confirm-stripe-purchase")
    @Operation(summary = "Confirm a Stripe checkout purchase and add AI tokens idempotently")
    public ResponseEntity<DemoPurchaseResponse> confirmStripePurchase(
            @Valid @RequestBody ConfirmStripePurchaseRequest request) {
        return ResponseEntity.ok(paymentService.confirmStripePurchase(request));
    }

    @PostMapping("/estimate")
    @Operation(summary = "Estimate AI token usage for a feature")
    public ResponseEntity<EstimateResponse> estimate(
            @Parameter(in = ParameterIn.HEADER, name = USER_ID_HEADER, required = true)
            @RequestHeader(name = USER_ID_HEADER, required = false) String userId,
            @Valid @RequestBody EstimateRequest request) {
        return ResponseEntity.ok(paymentService.estimate(requireUserId(userId), request));
    }

    @PostMapping("/reservations")
    @Operation(summary = "Reserve AI tokens before an internal LLM workflow")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tokens reserved"),
            @ApiResponse(responseCode = "402", description = "Insufficient AI Credit")
    })
    public ResponseEntity<ReservationResponse> createReservation(
            @Parameter(in = ParameterIn.HEADER, name = USER_ID_HEADER, required = true)
            @RequestHeader(name = USER_ID_HEADER, required = false) String userId,
            @Valid @RequestBody CreateReservationRequest request) {
        return ResponseEntity.ok(paymentService.createReservation(requireUserId(userId), request));
    }

    @PostMapping("/reservations/{reservationId}/commit")
    @Operation(summary = "Commit actual AI token usage for a reservation")
    public ResponseEntity<CommitReservationResponse> commitReservation(
            @Parameter(in = ParameterIn.HEADER, name = USER_ID_HEADER, required = true)
            @RequestHeader(name = USER_ID_HEADER, required = false) String userId,
            @PathVariable UUID reservationId,
            @Valid @RequestBody CommitReservationRequest request) {
        return ResponseEntity.ok(paymentService.commitReservation(requireUserId(userId), reservationId, request));
    }

    @PostMapping("/reservations/{reservationId}/release")
    @Operation(summary = "Release a reserved AI token hold")
    public ResponseEntity<ReleaseReservationResponse> releaseReservation(
            @Parameter(in = ParameterIn.HEADER, name = USER_ID_HEADER, required = true)
            @RequestHeader(name = USER_ID_HEADER, required = false) String userId,
            @PathVariable UUID reservationId,
            @RequestBody(required = false) ReleaseReservationRequest request) {
        String reason = request == null ? null : request.getReason();
        return ResponseEntity.ok(paymentService.releaseReservation(requireUserId(userId), reservationId, reason));
    }

    private String requireUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new MissingUserIdException();
        }
        return userId;
    }
}
