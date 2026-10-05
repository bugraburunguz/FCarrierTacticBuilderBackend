package com.fcareer.common.exception;

import static com.fcareer.common.constants.ErrorCodes.NOT_FOUND;

public class NotFoundException extends FcareerRuntimeException {
    public NotFoundException(String resource, Object id) {
        super(NOT_FOUND, "Resource not found", resource, id);
    }
}
