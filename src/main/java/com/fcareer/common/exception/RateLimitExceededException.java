package com.fcareer.common.exception;

import static com.fcareer.common.constants.ErrorCodes.RATE_LIMIT_EXCEEDED;

public class RateLimitExceededException extends FcareerRuntimeException {
    public RateLimitExceededException() {
        super(RATE_LIMIT_EXCEEDED, "Rate limit exceeded");
    }
}
