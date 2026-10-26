package com.fcareer.development.track;

import com.fcareer.fitengine.model.Attr;
import com.fcareer.ingestion.base.DumpMappings;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.fcareer.career.importer.CsvStream.toInt;
import static com.fcareer.career.importer.CsvStream.toLong;

/**
 * GTR-a: export tablolarından anlık görüntü. Kapsam: kulübü olan (playerTeam: içe aktarmanın kulüp bağı; milli takım değil) bütün oyuncular.
 * Oynama alanları kulüp bağından (form, leagueappearances) ve takımın lig maç sayısından; maç sayısı career_playermatchratinghistory'den (oyuncu
 * başına satır = maç, verified:false; tablo yoksa bilinmiyor).
 */
public final class GrowthSnapshotBuilder {

    private GrowthSnapshotBuilder() {
    }

    public static List<TrackedPlayer> build(LocalDate gameDate, List<Map<String, String>> players, List<Map<String, String>> links,
                                            List<Map<String, String>> leagueLinks, List<Map<String, String>> ratingHistory, Map<Long, Integer> playerTeam) {
        Map<Integer, Integer> teamMatches = new HashMap<>();
        rows(leagueLinks).forEach((r) -> {
            Integer team = toInt(r.get("teamid"));
            Integer played = toInt(r.get("nummatchesplayed"));
            if (Objects.nonNull(team) && Objects.nonNull(played)) {
                teamMatches.merge(team, played, Math::max);
            }
        });
        Map<Long, Map<String, String>> clubLink = new HashMap<>();
        rows(links).forEach((r) -> {
            Long player = toLong(r.get("playerid"));
            if (Objects.nonNull(player) && Objects.equals(toInt(r.get("teamid")), playerTeam.get(player))) {
                clubLink.put(player, r);
            }
        });
        Map<Long, Integer> matches = null;
        if (Objects.nonNull(ratingHistory)) {
            matches = new HashMap<>();
            for (Map<String, String> r : ratingHistory) {
                Long player = toLong(r.get("playerid"));
                if (Objects.nonNull(player)) {
                    matches.merge(player, 1, Integer::sum);
                }
            }
        }
        List<TrackedPlayer> out = new ArrayList<>();
        for (Map<String, String> r : rows(players)) {
            Long id = toLong(r.get("playerid"));
            Integer overall = toInt(r.get("overallrating"));
            Integer team = Objects.isNull(id) ? null : playerTeam.get(id);
            if (Objects.isNull(team) || Objects.isNull(overall)) {
                continue;
            }
            Map<String, String> link = clubLink.getOrDefault(id, Map.of());
            Map<Attr, Integer> attrs = new EnumMap<>(Attr.class);
            DumpMappings.ATTRIBUTE_COLUMNS.forEach((column, attr) -> {
                Integer v = toInt(r.get(column));
                if (Objects.nonNull(v)) {
                    attrs.put(attr, v);
                }
            });
            out.add(new TrackedPlayer(id, team, age(toInt(r.get("birthdate")), gameDate), DumpMappings.POSITION_BY_CODE.get(toInt(r.get("preferredposition1"))),
                    overall, toInt(r.get("potential")), toInt(link.get("form")), toInt(link.get("leagueappearances")), teamMatches.get(team),
                    Objects.isNull(matches) ? null : matches.getOrDefault(id, 0), TrackedPlayer.attrArray(attrs)));
        }
        out.sort(Comparator.comparingLong(TrackedPlayer::playerId));
        return out;
    }

    private static Integer age(Integer birth, LocalDate gameDate) {
        if (Objects.isNull(birth) || Objects.isNull(gameDate)) {
            return null;
        }
        return Period.between(LocalDate.ofEpochDay(birth + DumpMappings.BIRTHDATE_EPOCH_OFFSET), gameDate).getYears();
    }

    private static List<Map<String, String>> rows(List<Map<String, String>> rows) {
        return Objects.isNull(rows) ? List.of() : rows;
    }
}
