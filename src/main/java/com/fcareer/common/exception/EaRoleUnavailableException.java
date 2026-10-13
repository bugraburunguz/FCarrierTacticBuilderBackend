package com.fcareer.common.exception;

import org.springframework.http.HttpStatus;

public class EaRoleUnavailableException extends InvalidTacticException {
    public EaRoleUnavailableException(String detail) {
        super(detail);
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }
}
