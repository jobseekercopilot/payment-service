package com.jobseekercopilot.paymentservice.security;

public enum PaymentCaller {
    PAYMENT_GATEWAY,
    DOCUMENT_GENERATION_GATEWAY,
    CV_COVER_LETTER_SERVICE,
    STRIPE_GATEWAY,
    ACCOUNT_LIFECYCLE
}
