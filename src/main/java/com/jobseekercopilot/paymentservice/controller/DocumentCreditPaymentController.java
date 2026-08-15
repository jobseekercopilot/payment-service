package com.jobseekercopilot.paymentservice.controller;

import com.jobseekercopilot.paymentservice.dto.AccountPaymentLifecycleResponse;
import com.jobseekercopilot.paymentservice.dto.BindCheckoutSessionRequest;
import com.jobseekercopilot.paymentservice.dto.CheckoutReadinessResponse;
import com.jobseekercopilot.paymentservice.dto.CreateDocumentCreditReservationRequest;
import com.jobseekercopilot.paymentservice.dto.CreatePaymentOrderRequest;
import com.jobseekercopilot.paymentservice.dto.DocumentCreditCatalogResponse;
import com.jobseekercopilot.paymentservice.dto.DocumentCreditCommitResponse;
import com.jobseekercopilot.paymentservice.dto.DocumentCreditReleaseRequest;
import com.jobseekercopilot.paymentservice.dto.DocumentCreditReleaseResponse;
import com.jobseekercopilot.paymentservice.dto.DocumentCreditReservationResponse;
import com.jobseekercopilot.paymentservice.dto.DocumentCreditTransactionsResponse;
import com.jobseekercopilot.paymentservice.dto.DocumentCreditWalletResponse;
import com.jobseekercopilot.paymentservice.dto.PaymentOrderResponse;
import com.jobseekercopilot.paymentservice.dto.PaymentOrderStatusResponse;
import com.jobseekercopilot.paymentservice.dto.ProviderPaymentEventRequest;
import com.jobseekercopilot.paymentservice.dto.ProviderPaymentEventResponse;
import com.jobseekercopilot.paymentservice.exception.PaymentApiException;
import com.jobseekercopilot.paymentservice.security.PaymentIdentityFilter;
import com.jobseekercopilot.paymentservice.service.DocumentCreditService;
import com.jobseekercopilot.paymentservice.service.PaymentOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/payments")
@RequiredArgsConstructor
@SecurityRequirement(name = "serviceToken")
public class DocumentCreditPaymentController {
    private final DocumentCreditService documentCreditService;
    private final PaymentOrderService orderService;

    @GetMapping("/catalog")
    @Operation(
            operationId = "getDocumentCreditCatalog",
            summary = "Get the server-owned GBP document-credit catalog")
    public DocumentCreditCatalogResponse catalog() {
        return documentCreditService.catalog();
    }

    @GetMapping("/checkout-readiness")
    @Operation(operationId = "getDocumentCreditCheckoutReadiness")
    public CheckoutReadinessResponse readiness() {
        return orderService.readiness();
    }

    @GetMapping("/wallet")
    @Operation(
            operationId = "getDocumentCreditWallet",
            parameters = @Parameter(
                    name = PaymentIdentityFilter.OWNER_HEADER,
                    in = ParameterIn.HEADER,
                    required = true))
    public DocumentCreditWalletResponse wallet(
            @Parameter(
                    name = PaymentIdentityFilter.OWNER_HEADER,
                    in = ParameterIn.HEADER,
                    required = true)
            @RequestAttribute(PaymentIdentityFilter.OWNER_ATTRIBUTE) String owner) {
        return documentCreditService.wallet(owner);
    }

    @GetMapping("/transactions")
    @Operation(
            operationId = "listDocumentCreditTransactions",
            parameters = @Parameter(
                    name = PaymentIdentityFilter.OWNER_HEADER,
                    in = ParameterIn.HEADER,
                    required = true))
    public DocumentCreditTransactionsResponse transactions(
            @Parameter(
                    name = PaymentIdentityFilter.OWNER_HEADER,
                    in = ParameterIn.HEADER,
                    required = true)
            @RequestAttribute(PaymentIdentityFilter.OWNER_ATTRIBUTE) String owner,
            @RequestParam(defaultValue = "20") int limit) {
        return documentCreditService.transactions(owner, limit);
    }

    @PostMapping("/document-credit-reservations")
    @Operation(
            operationId = "createDocumentCreditReservation",
            parameters = @Parameter(
                    name = PaymentIdentityFilter.OWNER_HEADER,
                    in = ParameterIn.HEADER,
                    required = true))
    public DocumentCreditReservationResponse reserve(
            @Parameter(
                    name = PaymentIdentityFilter.OWNER_HEADER,
                    in = ParameterIn.HEADER,
                    required = true)
            @RequestAttribute(PaymentIdentityFilter.OWNER_ATTRIBUTE) String owner,
            @Valid @RequestBody CreateDocumentCreditReservationRequest request) {
        return documentCreditService.reserve(owner, request);
    }

    @GetMapping("/document-credit-reservations/{reservationId}")
    @Operation(
            operationId = "getDocumentCreditReservation",
            parameters = @Parameter(
                    name = PaymentIdentityFilter.OWNER_HEADER,
                    in = ParameterIn.HEADER,
                    required = true))
    public DocumentCreditReservationResponse reservation(
            @Parameter(
                    name = PaymentIdentityFilter.OWNER_HEADER,
                    in = ParameterIn.HEADER,
                    required = true)
            @RequestAttribute(PaymentIdentityFilter.OWNER_ATTRIBUTE) String owner,
            @PathVariable UUID reservationId) {
        return documentCreditService.reservation(owner, reservationId);
    }

    @PostMapping("/document-credit-reservations/{reservationId}/commit")
    @Operation(
            operationId = "commitDocumentCreditReservation",
            parameters = @Parameter(
                    name = PaymentIdentityFilter.OWNER_HEADER,
                    in = ParameterIn.HEADER,
                    required = true))
    public DocumentCreditCommitResponse commit(
            @Parameter(
                    name = PaymentIdentityFilter.OWNER_HEADER,
                    in = ParameterIn.HEADER,
                    required = true)
            @RequestAttribute(PaymentIdentityFilter.OWNER_ATTRIBUTE) String owner,
            @PathVariable UUID reservationId) {
        return documentCreditService.commit(owner, reservationId);
    }

    @PostMapping("/document-credit-reservations/{reservationId}/release")
    @Operation(
            operationId = "releaseDocumentCreditReservation",
            parameters = @Parameter(
                    name = PaymentIdentityFilter.OWNER_HEADER,
                    in = ParameterIn.HEADER,
                    required = true))
    public DocumentCreditReleaseResponse release(
            @Parameter(
                    name = PaymentIdentityFilter.OWNER_HEADER,
                    in = ParameterIn.HEADER,
                    required = true)
            @RequestAttribute(PaymentIdentityFilter.OWNER_ATTRIBUTE) String owner,
            @PathVariable UUID reservationId,
            @Valid @RequestBody(required = false) DocumentCreditReleaseRequest request) {
        return documentCreditService.release(
                owner, reservationId, request == null ? null : request.getReason());
    }

    @PostMapping("/orders")
    @Operation(
            operationId = "createDocumentCreditOrder",
            parameters = @Parameter(
                    name = PaymentIdentityFilter.OWNER_HEADER,
                    in = ParameterIn.HEADER,
                    required = true))
    public ResponseEntity<PaymentOrderResponse> createOrder(
            @Parameter(
                    name = PaymentIdentityFilter.OWNER_HEADER,
                    in = ParameterIn.HEADER,
                    required = true)
            @RequestAttribute(PaymentIdentityFilter.OWNER_ATTRIBUTE) String owner,
            @Parameter(name = "Idempotency-Key", in = ParameterIn.HEADER, required = true)
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreatePaymentOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.createOrder(owner, idempotencyKey, request));
    }

    @GetMapping("/orders/{orderId}")
    @Operation(
            operationId = "getDocumentCreditOrder",
            parameters = @Parameter(
                    name = PaymentIdentityFilter.OWNER_HEADER,
                    in = ParameterIn.HEADER,
                    required = true))
    public PaymentOrderResponse order(
            @Parameter(
                    name = PaymentIdentityFilter.OWNER_HEADER,
                    in = ParameterIn.HEADER,
                    required = true)
            @RequestAttribute(PaymentIdentityFilter.OWNER_ATTRIBUTE) String owner,
            @PathVariable UUID orderId) {
        return orderService.order(owner, orderId);
    }

    @GetMapping("/orders/{orderId}/status")
    @Operation(
            operationId = "getDocumentCreditOrderStatus",
            parameters = @Parameter(
                    name = PaymentIdentityFilter.OWNER_HEADER,
                    in = ParameterIn.HEADER,
                    required = true))
    public PaymentOrderStatusResponse orderStatus(
            @Parameter(
                    name = PaymentIdentityFilter.OWNER_HEADER,
                    in = ParameterIn.HEADER,
                    required = true)
            @RequestAttribute(PaymentIdentityFilter.OWNER_ATTRIBUTE) String owner,
            @PathVariable UUID orderId) {
        return orderService.orderStatus(owner, orderId);
    }

    @PostMapping("/orders/{orderId}/bind-stripe-session")
    @Operation(
            operationId = "bindStripeCheckoutSession",
            parameters = @Parameter(
                    name = PaymentIdentityFilter.OWNER_HEADER,
                    in = ParameterIn.HEADER,
                    required = true))
    public PaymentOrderResponse bind(
            @Parameter(
                    name = PaymentIdentityFilter.OWNER_HEADER,
                    in = ParameterIn.HEADER,
                    required = true)
            @RequestAttribute(PaymentIdentityFilter.OWNER_ATTRIBUTE) String owner,
            @PathVariable UUID orderId,
            @Valid @RequestBody BindCheckoutSessionRequest request) {
        return orderService.bindCheckout(owner, orderId, request);
    }

    @PostMapping("/orders/{orderId}/cancel")
    @Operation(
            operationId = "cancelDocumentCreditOrder",
            parameters = @Parameter(
                    name = PaymentIdentityFilter.OWNER_HEADER,
                    in = ParameterIn.HEADER,
                    required = true))
    public PaymentOrderResponse cancel(
            @Parameter(
                    name = PaymentIdentityFilter.OWNER_HEADER,
                    in = ParameterIn.HEADER,
                    required = true)
            @RequestAttribute(PaymentIdentityFilter.OWNER_ATTRIBUTE) String owner,
            @PathVariable UUID orderId) {
        return orderService.cancelOrder(owner, orderId);
    }

    @PostMapping("/provider-events/stripe")
    @Operation(operationId = "reconcileStripeProviderEvent")
    public ProviderPaymentEventResponse providerEvent(
            @Valid @RequestBody ProviderPaymentEventRequest request) {
        return orderService.providerEvent(request);
    }
}
