package com.fcareer.common.exception;

import org.springframework.http.HttpStatus;

import static com.fcareer.common.constants.ErrorCodes.SUBSCRIPTION_ALREADY_ACTIVE;

public class SubscriptionAlreadyActiveException extends FcareerRuntimeException {
    public SubscriptionAlreadyActiveException() {
        super(SUBSCRIPTION_ALREADY_ACTIVE, "Subscription already active");
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.CONFLICT;
    }
}
