package com.fcareer.fitengine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
@AllArgsConstructor
public class FitPlayer {

    public static final int PLAYSTYLE_PLUS = 2;

    private final Long id;
    private final String name;
    private final Integer age;
    private final Integer overall;
    private final List<String> positions;
    private final Map<Attr, Integer> attrs;
    private final Map<String, Integer> playstyles;
    private final AcceleRate acceleRate;
    private final Integer potential;
    private final Long valueEur;
    /** FitEngineV3 girdileri (E3-5); catalog'da bilinmiyorsa null. Aşinalık: rol kimliği → 1 (Role+) / 2 (Role++). */
    private final Integer gender;
    private final String preferredFoot;
    private final Integer weakFoot;
    private final Integer skillMoves;
    private final Integer heightCm;
    private final Map<String, Integer> roleFamiliarity;

    public int attr(Attr attr) {
        Integer value = attrs.get(attr);
        return value == null ? 0 : value;
    }

    public boolean hasPlaystyle(String playstyle) {
        return playstyles.containsKey(playstyle);
    }

    public boolean hasPlaystylePlus(String playstyle) {
        Integer level = playstyles.get(playstyle);
        return level != null && level >= PLAYSTYLE_PLUS;
    }
}
