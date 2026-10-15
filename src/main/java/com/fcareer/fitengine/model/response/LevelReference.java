package com.fcareer.fitengine.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * V3-06b: levelFit'in hangi düzeye göre ölçüldüğü. LEAGUE = kariyerde kullanıcının ligi (lig gücü = ligdeki kulüp güçlerinin medyanı, OVR);
 * OVR = lig gücü bilinmiyor, oyuncunun kendi OVR bandı (UT/katalog davranışı).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LevelReference(Kind kind, String league, Integer ovr) {

    public enum Kind {
        LEAGUE,
        OVR
    }

    public static LevelReference league(String league, int ovr) {
        return new LevelReference(Kind.LEAGUE, league, ovr);
    }

    public static LevelReference ownOvr() {
        return new LevelReference(Kind.OVR, null, null);
    }
}
