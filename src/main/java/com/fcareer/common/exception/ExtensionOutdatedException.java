package com.fcareer.common.exception;

import org.springframework.http.HttpStatus;

import static com.fcareer.common.constants.ErrorCodes.EXTENSION_OUTDATED;

public class ExtensionOutdatedException extends FcareerRuntimeException {
    public ExtensionOutdatedException(String minimum) {
        super(EXTENSION_OUTDATED, "Extension outdated", minimum);
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.UPGRADE_REQUIRED;
    }
}
