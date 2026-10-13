package com.fcareer.common.exception;

import static com.fcareer.common.constants.ErrorCodes.INVALID_VERIFICATION_TOKEN;

public class InvalidVerificationTokenException extends FcareerRuntimeException {
    public InvalidVerificationTokenException() {
        super(INVALID_VERIFICATION_TOKEN, "Invalid or expired verification token");
    }
}
