package com.jobseekercopilot.paymentservice.entity;

public enum PaymentOrderStatus {
    PENDING_CHECKOUT,
    CHECKOUT_OPEN,
    FULFILLED,
    EXPIRED,
    CANCELLED,
    REFUNDED,
    PARTIALLY_REFUNDED,
    DISPUTED,
    MANUAL_REVIEW
}
