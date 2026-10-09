package com.fcareer.common.exception;

import org.springframework.http.HttpStatus;

import static com.fcareer.common.constants.ErrorCodes.INSUFFICIENT_CREDIT;

public class InsufficientCreditException extends FcareerRuntimeException {
    public InsufficientCreditException() {
        super(INSUFFICIENT_CREDIT, "Insufficient credit");
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.PAYMENT_REQUIRED;
    }
}
