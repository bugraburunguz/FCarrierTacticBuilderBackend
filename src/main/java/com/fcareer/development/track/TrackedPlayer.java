package com.fcareer.development.track;

import com.fcareer.fitengine.model.Attr;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/**
 * GTR-a: anlık görüntüdeki oyuncu. playerId EA oyuncu kimliği (aynı kayıtta transferde sabit; üretilmiş oyuncular dahil). attrs Attr.values() sırasıyla,
 * bilinmeyen -1. Oynama alanları (export'ta ne varsa; bilinmiyorsa null): form (0–5), leagueApps (teamplayerlinks.leagueappearances), teamMatches
 * (takımın ligde oynadığı maç, leagueteamlinks.nummatchesplayed), matches (career_playermatchratinghistory satır sayısı, betik 2.0.4+).
 */
public record TrackedPlayer(long playerId, Integer teamId, Integer age, String position, int overall, Integer potential, Integer form, Integer leagueApps,
                            Integer teamMatches, Integer matches, int[] attrs) {

    public static final int UNKNOWN = -1;

    public static int[] attrArray(Map<Attr, Integer> values) {
        int[] out = new int[Attr.values().length];
        for (Attr a : Attr.values()) {
            out[a.ordinal()] = Objects.requireNonNullElse(values.get(a), UNKNOWN);
        }
        return out;
    }

    /** Attribute değeri; bilinmiyorsa null. */
    public Integer attr(Attr attr) {
        int v = attr.ordinal() < attrs.length ? attrs[attr.ordinal()] : UNKNOWN;
        return v == UNKNOWN ? null : v;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TrackedPlayer t && playerId == t.playerId && overall == t.overall && Objects.equals(teamId, t.teamId) && Objects.equals(age, t.age)
                && Objects.equals(position, t.position) && Objects.equals(potential, t.potential) && Objects.equals(form, t.form)
                && Objects.equals(leagueApps, t.leagueApps) && Objects.equals(teamMatches, t.teamMatches) && Objects.equals(matches, t.matches)
                && Arrays.equals(attrs, t.attrs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerId, overall, teamId, age, position, potential, form, leagueApps, teamMatches, matches) * 31 + Arrays.hashCode(attrs);
    }
}
