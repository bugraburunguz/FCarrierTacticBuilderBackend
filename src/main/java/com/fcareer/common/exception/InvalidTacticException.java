package com.fcareer.common.exception;

import org.springframework.http.HttpStatus;

import static com.fcareer.common.constants.ErrorCodes.INVALID_TACTIC;

public class InvalidTacticException extends FcareerRuntimeException {
    public InvalidTacticException(String detail) {
        super(INVALID_TACTIC, "Invalid tactic", detail);
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
