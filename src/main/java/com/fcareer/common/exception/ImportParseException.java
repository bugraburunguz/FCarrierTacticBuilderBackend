package com.fcareer.common.exception;

import static com.fcareer.common.constants.ErrorCodes.IMPORT_PARSE_ERROR;

public class ImportParseException extends FcareerRuntimeException {
    public ImportParseException(String detail) {
        super(IMPORT_PARSE_ERROR, "Import file could not be parsed", detail);
    }
}
