package com.fcareer.common.exception;

import org.springframework.http.HttpStatus;

import static com.fcareer.common.constants.ErrorCodes.CAREER_ACCESS_DENIED;

public class CareerAccessDeniedException extends FcareerRuntimeException {
    public CareerAccessDeniedException() {
        super(CAREER_ACCESS_DENIED, "Career does not belong to user");
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.FORBIDDEN;
    }
}
