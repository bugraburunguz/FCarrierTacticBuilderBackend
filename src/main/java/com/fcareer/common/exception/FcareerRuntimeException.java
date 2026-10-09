package com.fcareer.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class FcareerRuntimeException extends RuntimeException {

    private final String errorCode;
    private final transient Object[] args;

    public FcareerRuntimeException(String errorCode, String message, Object... args) {
        super(message);
        this.errorCode = errorCode;
        this.args = args;
    }

    public boolean isTechnical() {
        return false;
    }

    public HttpStatus httpStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
