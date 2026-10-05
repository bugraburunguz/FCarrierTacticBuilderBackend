package com.fcareer.common.exception;

import static com.fcareer.common.constants.ErrorCodes.INSUFFICIENT_BUDGET;

public class InsufficientBudgetException extends FcareerRuntimeException {
    public InsufficientBudgetException() {
        super(INSUFFICIENT_BUDGET, "Insufficient transfer budget");
    }
}
