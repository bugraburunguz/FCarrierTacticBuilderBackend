package com.fcareer.common.exception;

import org.springframework.http.HttpStatus;

import static com.fcareer.common.constants.ErrorCodes.RATE_LIMIT_EXCEEDED;

public class RateLimitExceededException extends FcareerRuntimeException {
    public RateLimitExceededException() {
        super(RATE_LIMIT_EXCEEDED, "Rate limit exceeded");
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.TOO_MANY_REQUESTS;
    }
}
