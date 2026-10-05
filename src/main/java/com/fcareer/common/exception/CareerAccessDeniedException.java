package com.fcareer.common.exception;

import static com.fcareer.common.constants.ErrorCodes.CAREER_ACCESS_DENIED;

public class CareerAccessDeniedException extends FcareerRuntimeException {
    public CareerAccessDeniedException() {
        super(CAREER_ACCESS_DENIED, "Career does not belong to user");
    }
}
