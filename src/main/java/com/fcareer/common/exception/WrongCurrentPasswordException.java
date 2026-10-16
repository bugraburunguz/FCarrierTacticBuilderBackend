package com.fcareer.common.exception;

import org.springframework.http.HttpStatus;

import static com.fcareer.common.constants.ErrorCodes.WRONG_CURRENT_PASSWORD;

/** Şifre değiştirirken mevcut şifre yanlış: 422 (401 değil — istemci oturumu yenilemeye/çıkışa yönelmesin). */
public class WrongCurrentPasswordException extends FcareerRuntimeException {
    public WrongCurrentPasswordException() {
        super(WRONG_CURRENT_PASSWORD, "Wrong current password");
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }
}
