package com.fcareer.common.exception;

import static com.fcareer.common.constants.ErrorCodes.INVALID_CREDENTIALS;

public class InvalidCredentialsException extends FcareerRuntimeException {
    public InvalidCredentialsException() {
        super(INVALID_CREDENTIALS, "Invalid credentials");
    }
}
