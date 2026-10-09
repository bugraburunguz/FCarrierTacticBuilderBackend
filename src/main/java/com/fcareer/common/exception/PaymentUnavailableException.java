package com.fcareer.common.exception;

import org.springframework.http.HttpStatus;

import static com.fcareer.common.constants.ErrorCodes.PAYMENT_UNAVAILABLE;

public class PaymentUnavailableException extends FcareerRuntimeException {
    public PaymentUnavailableException() {
        super(PAYMENT_UNAVAILABLE, "Payment provider is not configured");
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.SERVICE_UNAVAILABLE;
    }
}
