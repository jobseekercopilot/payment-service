package com.jobseekercopilot.paymentservice.exception;

public class InsufficientTokensException extends RuntimeException {
    public InsufficientTokensException() {
        super("Insufficient AI Credit");
    }
}
