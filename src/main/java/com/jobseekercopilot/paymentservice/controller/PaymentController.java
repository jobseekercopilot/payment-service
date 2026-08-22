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
import com.jobseekercopilot.paymentservice.dto.ReservationStatusResponse;
import com.jobseekercopilot.paymentservice.dto.TransactionsResponse;
import com.jobseekercopilot.paymentservice.dto.WalletSummaryResponse;
import com.jobseekercopilot.paymentservice.security.PaymentIdentityFilter;
import com.jobseekercopilot.paymentservice.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@SecurityRequirement(name = "serviceToken")
public class PaymentController {
    private final PaymentService paymentService;

    @GetMapping("/wallet")
    @Operation(
            summary = "Get or create the user's AI token wallet",
            parameters = @Parameter(
                    in = ParameterIn.HEADER,
                    name = PaymentIdentityFilter.OWNER_HEADER,
                    required = true))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Wallet summary returned"),
            @ApiResponse(responseCode = "401", description = "Service authentication failed")
    })
    public ResponseEntity<WalletSummaryResponse> wallet(
            @RequestAttribute(PaymentIdentityFilter.OWNER_ATTRIBUTE) String owner) {
        return ResponseEntity.ok(paymentService.wallet(owner));
    }

    @GetMapping("/transactions")
    @Operation(
            summary = "Get the user's AI token transaction history",
            parameters = @Parameter(
                    in = ParameterIn.HEADER,
                    name = PaymentIdentityFilter.OWNER_HEADER,
                    required = true))
    public ResponseEntity<TransactionsResponse> transactions(
            @RequestAttribute(PaymentIdentityFilter.OWNER_ATTRIBUTE) String owner,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return ResponseEntity.ok(paymentService.transactions(owner, limit));
    }

    @GetMapping("/pricing")
    @Operation(summary = "Get active AI token pricing plans")
    public ResponseEntity<PricingPlansResponse> pricing() {
        return ResponseEntity.ok(paymentService.pricing());
    }

    @PostMapping("/demo-purchase")
    @Operation(
            summary = "Demo purchase AI tokens",
            parameters = @Parameter(
                    in = ParameterIn.HEADER,
                    name = PaymentIdentityFilter.OWNER_HEADER,
                    required = true))
    public ResponseEntity<DemoPurchaseResponse> demoPurchase(
            @RequestAttribute(PaymentIdentityFilter.OWNER_ATTRIBUTE) String owner,
            @Valid @RequestBody DemoPurchaseRequest request) {
        return ResponseEntity.ok(paymentService.demoPurchase(owner, request.getPricingPlanId()));
    }

    @PostMapping("/confirm-stripe-purchase")
    @Operation(
            summary = "Confirm a Stripe checkout purchase and add AI tokens idempotently",
            parameters = @Parameter(
                    in = ParameterIn.HEADER,
                    name = PaymentIdentityFilter.OWNER_HEADER,
                    required = true))
    public ResponseEntity<DemoPurchaseResponse> confirmStripePurchase(
            @RequestAttribute(PaymentIdentityFilter.OWNER_ATTRIBUTE) String owner,
            @Valid @RequestBody ConfirmStripePurchaseRequest request) {
        return ResponseEntity.ok(paymentService.confirmStripePurchase(owner, request));
    }

    @PostMapping("/estimate")
    @Operation(
            summary = "Estimate AI token usage for a feature",
            parameters = @Parameter(
                    in = ParameterIn.HEADER,
                    name = PaymentIdentityFilter.OWNER_HEADER,
                    required = true))
    public ResponseEntity<EstimateResponse> estimate(
            @RequestAttribute(PaymentIdentityFilter.OWNER_ATTRIBUTE) String owner,
            @Valid @RequestBody EstimateRequest request) {
        return ResponseEntity.ok(paymentService.estimate(owner, request));
    }

    @PostMapping("/reservations")
    @Operation(
            summary = "Reserve AI tokens before an internal LLM workflow",
            parameters = @Parameter(
                    in = ParameterIn.HEADER,
                    name = PaymentIdentityFilter.OWNER_HEADER,
                    required = true))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tokens reserved"),
            @ApiResponse(responseCode = "402", description = "Insufficient AI Credit")
    })
    public ResponseEntity<ReservationResponse> createReservation(
            @RequestAttribute(PaymentIdentityFilter.OWNER_ATTRIBUTE) String owner,
            @Valid @RequestBody CreateReservationRequest request) {
        return ResponseEntity.ok(paymentService.createReservation(owner, request));
    }

    @GetMapping("/reservations/{reservationId}")
    @Operation(
            summary = "Get owner-scoped reservation lifecycle and recovery evidence",
            parameters = @Parameter(
                    in = ParameterIn.HEADER,
                    name = PaymentIdentityFilter.OWNER_HEADER,
                    required = true))
    public ResponseEntity<ReservationStatusResponse> reservationStatus(
            @RequestAttribute(PaymentIdentityFilter.OWNER_ATTRIBUTE) String owner,
            @PathVariable UUID reservationId) {
        return ResponseEntity.ok(paymentService.reservationStatus(owner, reservationId));
    }

    @PostMapping("/reservations/{reservationId}/commit")
    @Operation(
            summary = "Commit actual AI token usage for a reservation",
            parameters = @Parameter(
                    in = ParameterIn.HEADER,
                    name = PaymentIdentityFilter.OWNER_HEADER,
                    required = true))
    public ResponseEntity<CommitReservationResponse> commitReservation(
            @RequestAttribute(PaymentIdentityFilter.OWNER_ATTRIBUTE) String owner,
            @PathVariable UUID reservationId,
            @Valid @RequestBody CommitReservationRequest request) {
        return ResponseEntity.ok(paymentService.commitReservation(owner, reservationId, request));
    }

    @PostMapping("/reservations/{reservationId}/release")
    @Operation(
            summary = "Release a reserved AI token hold",
            parameters = @Parameter(
                    in = ParameterIn.HEADER,
                    name = PaymentIdentityFilter.OWNER_HEADER,
                    required = true))
    public ResponseEntity<ReleaseReservationResponse> releaseReservation(
            @RequestAttribute(PaymentIdentityFilter.OWNER_ATTRIBUTE) String owner,
            @PathVariable UUID reservationId,
            @RequestBody(required = false) ReleaseReservationRequest request) {
        String reason = request == null ? null : request.getReason();
        return ResponseEntity.ok(paymentService.releaseReservation(owner, reservationId, reason));
    }
}
