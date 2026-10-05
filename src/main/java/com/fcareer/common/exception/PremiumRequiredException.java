package com.fcareer.common.exception;

import static com.fcareer.common.constants.ErrorCodes.PREMIUM_REQUIRED;

public class PremiumRequiredException extends FcareerRuntimeException {
    public PremiumRequiredException() {
        super(PREMIUM_REQUIRED, "Premium subscription required");
    }
}
