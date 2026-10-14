package com.fcareer.recommendation.pipeline;

import com.fcareer.catalog.repository.CareerPlayerRepository.TeamStrengthRow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Kulüp / lig gücü (E3-6b, elle lig tablosu yerine kayıttan türetilir):
 *   kulüp gücü = kayıttaki kulübün en iyi N (16) oyuncusunun OVR ortalaması; oyuncu sayısı minPlayers'ın altındaysa kulübün lig gücüne düşer;
 *   lig gücü   = ligdeki (yeterli oyunculu) kulüp güçlerinin medyanı; en az minClubs kulüp yoksa tanımsızdır.
 */
public final class ClubStrength {

    private final Map<String, Double> club = new HashMap<>();
    private final Map<String, String> leagueOf = new HashMap<>();
    private final Map<String, Double> league = new HashMap<>();

    private ClubStrength() {
    }

    public static ClubStrength of(List<TeamStrengthRow> rows, int minPlayers, int minClubs) {
        ClubStrength result = new ClubStrength();
        Map<String, List<Double>> byLeague = new HashMap<>();
        for (TeamStrengthRow row : rows) {
            result.leagueOf.put(row.key(), row.league());
            if (row.players() >= minPlayers) {
                result.club.put(row.key(), row.strength());
                if (Objects.nonNull(row.league())) {
                    byLeague.computeIfAbsent(row.league(), (k) -> new ArrayList<>()).add(row.strength());
                }
            }
        }
        byLeague.forEach((name, values) -> {
            if (values.size() >= minClubs) {
                result.league.put(name, median(values));
            }
        });
        return result;
    }

    /** Kulüp gücü; örneklem küçükse (ya da kulüp tanımsızsa kendi ligi biliniyorsa) lig gücü, hiçbiri yoksa null. */
    public Double clubStrength(String key) {
        if (Objects.isNull(key)) {
            return null;
        }
        Double own = club.get(key);
        if (Objects.nonNull(own)) {
            return own;
        }
        String leagueName = leagueOf.get(key);
        return Objects.isNull(leagueName) ? null : league.get(leagueName);
    }

    public Double leagueStrength(String leagueName) {
        return Objects.isNull(leagueName) ? null : league.get(leagueName);
    }

    public String leagueOf(String key) {
        return leagueOf.get(key);
    }

    public Map<String, Double> allLeagues() {
        return Map.copyOf(league);
    }

    private static double median(List<Double> values) {
        List<Double> sorted = values.stream().sorted(Comparator.naturalOrder()).toList();
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }
}
