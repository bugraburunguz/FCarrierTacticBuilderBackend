package com.fcareer.common.exception;

import org.springframework.http.HttpStatus;

import static com.fcareer.common.constants.ErrorCodes.SQUAD_TOO_SMALL;

/** Kadroda 11'den az oyuncu: diziliş/başlangıç önerisi kurulamaz (iş kuralı, 422). */
public class SquadTooSmallException extends FcareerRuntimeException {
    public SquadTooSmallException(int size) {
        super(SQUAD_TOO_SMALL, "Squad too small : " + size);
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }
}
