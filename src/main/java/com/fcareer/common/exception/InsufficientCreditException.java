package com.fcareer.common.exception;

import static com.fcareer.common.constants.ErrorCodes.INSUFFICIENT_CREDIT;

public class InsufficientCreditException extends FcareerRuntimeException {
    public InsufficientCreditException() {
        super(INSUFFICIENT_CREDIT, "Insufficient credit");
    }
}
