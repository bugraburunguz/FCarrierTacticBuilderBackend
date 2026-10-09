package com.fcareer.common.exception;

import org.springframework.http.HttpStatus;

import static com.fcareer.common.constants.ErrorCodes.EMAIL_ALREADY_REGISTERED;

public class EmailAlreadyRegisteredException extends FcareerRuntimeException {
    public EmailAlreadyRegisteredException() {
        super(EMAIL_ALREADY_REGISTERED, "Email already registered");
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.CONFLICT;
    }
}
