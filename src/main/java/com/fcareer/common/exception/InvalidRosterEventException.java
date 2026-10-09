package com.fcareer.common.exception;

import org.springframework.http.HttpStatus;

import static com.fcareer.common.constants.ErrorCodes.INVALID_ROSTER_EVENT;

public class InvalidRosterEventException extends FcareerRuntimeException {
    public InvalidRosterEventException(String detail) {
        super(INVALID_ROSTER_EVENT, "Invalid roster event", detail);
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }
}
