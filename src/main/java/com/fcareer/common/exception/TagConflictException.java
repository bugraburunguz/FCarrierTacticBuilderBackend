package com.fcareer.common.exception;

import org.springframework.http.HttpStatus;

import static com.fcareer.common.constants.ErrorCodes.TAG_CONFLICT;

public class TagConflictException extends FcareerRuntimeException {
    public TagConflictException(String tagA, String tagB) {
        super(TAG_CONFLICT, "Conflicting behavior tags", tagA, tagB);
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.CONFLICT;
    }
}
