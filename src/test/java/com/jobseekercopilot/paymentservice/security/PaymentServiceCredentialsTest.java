package com.jobseekercopilot.paymentservice.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentServiceCredentialsTest {
    private static final String PAYMENT = "payment-gateway-token-000000000000000001";
    private static final String DOCUMENT_GENERATION =
            "document-generation-gateway-token-000000001";
    private static final String CV = "cv-cover-letter-token-0000000000000000001";
    private static final String STRIPE = "stripe-gateway-token-0000000000000000001";

    @Test
    void authenticatesEachDedicatedCaller() {
        PaymentServiceCredentials credentials =
                new PaymentServiceCredentials(PAYMENT, DOCUMENT_GENERATION, CV, STRIPE);

        assertThat(credentials.authenticate(PAYMENT)).isEqualTo(PaymentCaller.PAYMENT_GATEWAY);
        assertThat(credentials.authenticate(DOCUMENT_GENERATION))
                .isEqualTo(PaymentCaller.DOCUMENT_GENERATION_GATEWAY);
        assertThat(credentials.authenticate(CV)).isEqualTo(PaymentCaller.CV_COVER_LETTER_SERVICE);
        assertThat(credentials.authenticate(STRIPE)).isEqualTo(PaymentCaller.STRIPE_GATEWAY);
        assertThat(credentials.authenticate("forged")).isNull();
        assertThat(credentials.authenticate(null)).isNull();
    }

    @Test
    void rejectsMissingShortOrReusedCredentials() {
        assertThatThrownBy(() -> new PaymentServiceCredentials("", DOCUMENT_GENERATION, CV, STRIPE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Payment Gateway");
        assertThatThrownBy(() -> new PaymentServiceCredentials("short", DOCUMENT_GENERATION, CV, STRIPE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> new PaymentServiceCredentials(
                PAYMENT, DOCUMENT_GENERATION, PAYMENT, STRIPE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Payment service identity tokens must be distinct.");
        assertThatThrownBy(() -> new PaymentServiceCredentials(
                PAYMENT, PAYMENT, CV, STRIPE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Payment service identity tokens must be distinct.");
    }
}
