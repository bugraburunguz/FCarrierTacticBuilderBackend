package com.fcareer.common.exception;

import org.springframework.http.HttpStatus;

import static com.fcareer.common.constants.ErrorCodes.DELETE_EMAIL_MISMATCH;
import static com.fcareer.common.constants.ErrorCodes.LAST_ADMIN;

/** Hesap silme reddi (PROD-PREP 4): yazılan e-posta eşleşmiyor (422) ya da son yönetici (409). */
public class AccountDeletionRejectedException extends FcareerRuntimeException {

    private final HttpStatus status;

    private AccountDeletionRejectedException(String code, String message, HttpStatus status) {
        super(code, message);
        this.status = status;
    }

    public static AccountDeletionRejectedException emailMismatch() {
        return new AccountDeletionRejectedException(DELETE_EMAIL_MISMATCH, "Delete confirmation e-mail mismatch", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public static AccountDeletionRejectedException lastAdmin() {
        return new AccountDeletionRejectedException(LAST_ADMIN, "Last admin cannot be deleted", HttpStatus.CONFLICT);
    }

    @Override
    public HttpStatus httpStatus() {
        return status;
    }
}
