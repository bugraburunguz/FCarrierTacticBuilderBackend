package com.fcareer.common.exception;

import org.springframework.http.HttpStatus;

import static com.fcareer.common.constants.ErrorCodes.PREMIUM_REQUIRED;

public class PremiumRequiredException extends FcareerRuntimeException {
    public PremiumRequiredException() {
        super(PREMIUM_REQUIRED, "Premium subscription required");
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.PAYMENT_REQUIRED;
    }
}
