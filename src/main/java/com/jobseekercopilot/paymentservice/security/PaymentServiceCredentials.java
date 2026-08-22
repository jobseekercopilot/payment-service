package com.jobseekercopilot.paymentservice.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class PaymentServiceCredentials {
    static final int MINIMUM_TOKEN_BYTES = 32;

    private final String paymentGatewayToken;
    private final String documentGenerationGatewayToken;
    private final String cvCoverLetterToken;
    private final String stripeGatewayToken;
    private final String accountLifecycleToken;

    PaymentServiceCredentials(
            String paymentGatewayToken,
            String documentGenerationGatewayToken,
            String cvCoverLetterToken,
            String stripeGatewayToken) {
        this(paymentGatewayToken,
                documentGenerationGatewayToken,
                cvCoverLetterToken,
                stripeGatewayToken,
                "account-lifecycle-compatibility-token-000001");
    }

    @Autowired
    public PaymentServiceCredentials(
            @Value("${payment.security.payment-gateway-token}") String paymentGatewayToken,
            @Value("${payment.security.document-generation-gateway-token}")
            String documentGenerationGatewayToken,
            @Value("${payment.security.cv-cover-letter-token}") String cvCoverLetterToken,
            @Value("${payment.security.stripe-gateway-token}") String stripeGatewayToken,
            @Value("${payment.security.account-lifecycle-token}") String accountLifecycleToken) {
        this.paymentGatewayToken = validate(paymentGatewayToken, "Payment Gateway service token");
        this.documentGenerationGatewayToken = validate(
                documentGenerationGatewayToken,
                "Document Generation Gateway service token");
        this.cvCoverLetterToken = validate(cvCoverLetterToken, "CV and Cover Letter service token");
        this.stripeGatewayToken = validate(stripeGatewayToken, "Stripe Gateway service token");
        this.accountLifecycleToken = validate(
                accountLifecycleToken, "Account Lifecycle service token");
        requireDistinct(
                this.paymentGatewayToken,
                this.documentGenerationGatewayToken,
                this.cvCoverLetterToken,
                this.stripeGatewayToken,
                this.accountLifecycleToken);
    }

    public PaymentCaller authenticate(String supplied) {
        if (matches(supplied, paymentGatewayToken)) {
            return PaymentCaller.PAYMENT_GATEWAY;
        }
        if (matches(supplied, documentGenerationGatewayToken)) {
            return PaymentCaller.DOCUMENT_GENERATION_GATEWAY;
        }
        if (matches(supplied, cvCoverLetterToken)) {
            return PaymentCaller.CV_COVER_LETTER_SERVICE;
        }
        if (matches(supplied, stripeGatewayToken)) {
            return PaymentCaller.STRIPE_GATEWAY;
        }
        if (matches(supplied, accountLifecycleToken)) {
            return PaymentCaller.ACCOUNT_LIFECYCLE;
        }
        return null;
    }

    private static String validate(String value, String label) {
        if (value == null
                || value.isBlank()
                || value.getBytes(StandardCharsets.UTF_8).length < MINIMUM_TOKEN_BYTES) {
            throw new IllegalStateException(label + " must contain at least 32 bytes.");
        }
        return value;
    }

    private static void requireDistinct(String... values) {
        for (int first = 0; first < values.length; first++) {
            for (int second = first + 1; second < values.length; second++) {
                if (matches(values[first], values[second])) {
                    throw new IllegalStateException("Payment service identity tokens must be distinct.");
                }
            }
        }
    }

    private static boolean matches(String supplied, String expected) {
        return supplied != null && MessageDigest.isEqual(
                supplied.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }
}
