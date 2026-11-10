package com.fcareer.common.exception;

import org.springframework.http.HttpStatus;

import static com.fcareer.common.constants.ErrorCodes.LEAGUE_OUT_OF_REACH;

/** FCA-5: öneri isteğindeki lig kulübün erişebileceği lig kademelerinde değil; sonuç hep boş olurdu. Ücretli iş başlamadan döner (kredi düşmez). */
public class LeagueOutOfReachException extends FcareerRuntimeException {
    public LeagueOutOfReachException() {
        super(LEAGUE_OUT_OF_REACH, "League is outside the reachable tiers");
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }
}
