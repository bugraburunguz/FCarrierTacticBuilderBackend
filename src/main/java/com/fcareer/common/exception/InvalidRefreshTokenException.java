package com.fcareer.common.exception;

import static com.fcareer.common.constants.ErrorCodes.INVALID_REFRESH_TOKEN;

public class InvalidRefreshTokenException extends FcareerRuntimeException {
    public InvalidRefreshTokenException() {
        super(INVALID_REFRESH_TOKEN, "Invalid or expired refresh token");
    }
}
