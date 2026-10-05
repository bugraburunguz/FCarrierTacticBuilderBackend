package com.fcareer.common.exception;

import static com.fcareer.common.constants.ErrorCodes.DOWNSTREAM_ERROR;

public class DownstreamServiceException extends FcareerRuntimeException {
    public DownstreamServiceException(String service) {
        super(DOWNSTREAM_ERROR, "Downstream service error", service);
    }

    @Override
    public boolean isTechnical() {
        return true;
    }
}
