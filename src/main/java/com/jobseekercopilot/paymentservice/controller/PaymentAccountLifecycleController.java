package com.jobseekercopilot.paymentservice.controller;

import com.jobseekercopilot.paymentservice.dto.AccountPaymentExportResponse;
import com.jobseekercopilot.paymentservice.dto.AccountPaymentLifecycleResponse;
import com.jobseekercopilot.paymentservice.exception.PaymentApiException;
import com.jobseekercopilot.paymentservice.security.PaymentIdentityFilter;
import com.jobseekercopilot.paymentservice.service.PaymentAccountLifecycleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v2/payments/owners")
@RequiredArgsConstructor
@SecurityRequirement(name = "serviceToken")
public class PaymentAccountLifecycleController {
    private final PaymentAccountLifecycleService lifecycleService;

    @GetMapping("/{owner}/export")
    @Operation(
            operationId = "exportPaymentAccountMetadata",
            parameters = @Parameter(
                    name = PaymentIdentityFilter.OWNER_HEADER,
                    in = ParameterIn.HEADER,
                    required = true))
    public AccountPaymentExportResponse export(
            @PathVariable String owner,
            @Parameter(
                    name = PaymentIdentityFilter.OWNER_HEADER,
                    in = ParameterIn.HEADER,
                    required = true)
            @RequestAttribute(PaymentIdentityFilter.OWNER_ATTRIBUTE) String authenticatedOwner) {
        requireMatchingOwner(owner, authenticatedOwner);
        return lifecycleService.export(owner);
    }

    @PostMapping("/{owner}/revoke-access")
    @Operation(
            operationId = "revokePaymentAccountAccess",
            parameters = @Parameter(
                    name = PaymentIdentityFilter.OWNER_HEADER,
                    in = ParameterIn.HEADER,
                    required = true))
    public AccountPaymentLifecycleResponse revoke(
            @PathVariable String owner,
            @Parameter(
                    name = PaymentIdentityFilter.OWNER_HEADER,
                    in = ParameterIn.HEADER,
                    required = true)
            @RequestAttribute(PaymentIdentityFilter.OWNER_ATTRIBUTE) String authenticatedOwner) {
        requireMatchingOwner(owner, authenticatedOwner);
        return lifecycleService.revokeAccess(owner);
    }

    private void requireMatchingOwner(String owner, String authenticatedOwner) {
        if (!Objects.equals(owner, authenticatedOwner)) {
            throw new PaymentApiException(HttpStatus.BAD_REQUEST, "OWNER_MISMATCH",
                    "The lifecycle owner does not match the trusted owner context.");
        }
    }
}
