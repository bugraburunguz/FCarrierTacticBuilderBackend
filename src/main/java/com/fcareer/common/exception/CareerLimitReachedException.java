package com.fcareer.common.exception;

import org.springframework.http.HttpStatus;

import static com.fcareer.common.constants.ErrorCodes.CAREER_LIMIT_REACHED;

/** GUV-2 (F4): kullanıcının açık kariyer sayısı üst sınırda; yeni kariyer için önce biri silinmeli (iş kuralı, 422). */
public class CareerLimitReachedException extends FcareerRuntimeException {
    public CareerLimitReachedException(int limit) {
        super(CAREER_LIMIT_REACHED, "Career limit reached : " + limit, limit);
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }
}
