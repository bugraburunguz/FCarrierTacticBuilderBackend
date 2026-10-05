package com.fcareer.common.exception;

import static com.fcareer.common.constants.ErrorCodes.EMAIL_ALREADY_REGISTERED;

public class EmailAlreadyRegisteredException extends FcareerRuntimeException {
    public EmailAlreadyRegisteredException() {
        super(EMAIL_ALREADY_REGISTERED, "Email already registered");
    }
}
