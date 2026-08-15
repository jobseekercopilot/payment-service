package com.jobseekercopilot.paymentservice.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class PaymentApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public PaymentApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
