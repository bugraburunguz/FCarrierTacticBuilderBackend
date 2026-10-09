package com.fcareer.common.exception;

import org.springframework.http.HttpStatus;

import static com.fcareer.common.constants.ErrorCodes.PAYLOAD_TOO_LARGE;

public class PayloadTooLargeException extends FcareerRuntimeException {
    public PayloadTooLargeException() {
        super(PAYLOAD_TOO_LARGE, "Payload too large");
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.PAYLOAD_TOO_LARGE;
    }
}
