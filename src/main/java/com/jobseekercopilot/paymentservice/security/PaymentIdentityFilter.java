package com.jobseekercopilot.paymentservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public final class PaymentIdentityFilter extends OncePerRequestFilter {
    public static final String SERVICE_TOKEN_HEADER = "X-Service-Token";
    public static final String OWNER_HEADER = "X-Payment-Owner";
    public static final String OWNER_ATTRIBUTE = "paymentOwner";
    public static final String CALLER_ATTRIBUTE = "paymentCaller";

    private static final String PROTECTED_PATH = "/api/v1/payments";
    private static final String LEGACY_OWNER_HEADER = "X-User-Id";
    private static final int MAXIMUM_OWNER_LENGTH = 128;
    private static final Set<String> PAYMENT_GATEWAY_PATHS = Set.of(
            "/api/v1/payments/wallet",
            "/api/v1/payments/transactions",
            "/api/v1/payments/pricing",
            "/api/v1/payments/demo-purchase",
            "/api/v1/payments/estimate");

    private final PaymentServiceCredentials credentials;
    private final ObjectMapper objectMapper;

    public PaymentIdentityFilter(
            PaymentServiceCredentials credentials,
            ObjectMapper objectMapper) {
        this.credentials = credentials;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.equals(PROTECTED_PATH) || path.startsWith(PROTECTED_PATH + "/"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        List<String> tokens = headers(request, SERVICE_TOKEN_HEADER);
        PaymentCaller caller = tokens.size() == 1
                ? credentials.authenticate(tokens.get(0))
                : null;
        if (caller == null) {
            reject(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "SERVICE_AUTHENTICATION_REQUIRED",
                    "Valid service authentication is required.");
            return;
        }

        if (!isAuthorized(caller, request)) {
            reject(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "SERVICE_NOT_AUTHORIZED",
                    "The authenticated service is not authorized for this operation.");
            return;
        }

        if (!headers(request, LEGACY_OWNER_HEADER).isEmpty()) {
            reject(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "CALLER_IDENTITY_REJECTED",
                    "Caller-selected identity is not accepted.");
            return;
        }

        if (requiresOwner(request)) {
            List<String> owners = headers(request, OWNER_HEADER);
            if (owners.size() != 1 || !validOwner(owners.get(0))) {
                reject(
                        response,
                        HttpServletResponse.SC_BAD_REQUEST,
                        "PAYMENT_OWNER_REQUIRED",
                        "Exactly one valid payment owner is required.");
                return;
            }
            request.setAttribute(OWNER_ATTRIBUTE, owners.get(0).trim());
        }

        request.setAttribute(CALLER_ATTRIBUTE, caller);
        filterChain.doFilter(request, response);
    }

    private boolean isAuthorized(PaymentCaller caller, HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!"POST".equals(request.getMethod()) && !"GET".equals(request.getMethod())) {
            return false;
        }
        return switch (caller) {
            case PAYMENT_GATEWAY -> PAYMENT_GATEWAY_PATHS.contains(path);
            case DOCUMENT_GENERATION_GATEWAY, CV_COVER_LETTER_SERVICE ->
                    ("POST".equals(request.getMethod())
                            && (path.equals("/api/v1/payments/reservations")
                            || path.matches("/api/v1/payments/reservations/[^/]+/(commit|release)")))
                    || ("GET".equals(request.getMethod())
                            && path.matches("/api/v1/payments/reservations/[^/]+"));
            case STRIPE_GATEWAY -> "POST".equals(request.getMethod())
                    && path.equals("/api/v1/payments/confirm-stripe-purchase");
        };
    }

    private boolean requiresOwner(HttpServletRequest request) {
        return !request.getRequestURI().equals("/api/v1/payments/pricing");
    }

    private boolean validOwner(String owner) {
        if (!StringUtils.hasText(owner)) {
            return false;
        }
        String trimmed = owner.trim();
        return trimmed.length() <= MAXIMUM_OWNER_LENGTH
                && !trimmed.contains(",")
                && trimmed.chars().noneMatch(Character::isISOControl);
    }

    private List<String> headers(HttpServletRequest request, String name) {
        return Collections.list(request.getHeaders(name));
    }

    private void reject(
            HttpServletResponse response,
            int status,
            String code,
            String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                new PaymentIdentityError(code, message));
    }
}
