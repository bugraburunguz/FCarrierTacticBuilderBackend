package com.fcareer.common.exception;

import static com.fcareer.common.constants.ErrorCodes.INVALID_ROSTER_EVENT;

public class InvalidRosterEventException extends FcareerRuntimeException {
    public InvalidRosterEventException(String detail) {
        super(INVALID_ROSTER_EVENT, "Invalid roster event", detail);
    }
}
