package com.fcareer.common.exception;

import org.springframework.http.HttpStatus;

import static com.fcareer.common.constants.ErrorCodes.CAREER_CREATE_LIMIT_EXCEEDED;

/** GUV-2 (F4): kullanıcı bugün izin verilen sayıda kariyer açtı (sil-aç döngüsüyle katalog kopyalamayı keser, 429). */
public class CareerCreateLimitExceededException extends FcareerRuntimeException {
    public CareerCreateLimitExceededException(int limit) {
        super(CAREER_CREATE_LIMIT_EXCEEDED, "Daily career create limit exceeded : " + limit, limit);
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.TOO_MANY_REQUESTS;
    }
}
