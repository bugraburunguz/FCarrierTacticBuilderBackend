package com.fcareer.career.match;

import com.fcareer.career.match.MatchCenterModels.TeamMatchInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchProfileImporter {

    static final String FORMATIONS = "formations";
    static final String TEAMS = "teams";
    static final String LINKS = "teamplayerlinks";
    private static final int FORMATION_SLOTS = 11;

    private final MatchCenterRepository repository;

    public void apply(long careerId, Map<String, List<Map<String, String>>> tables, Map<Long, Long> eaToBase, Integer userTeam) {
        if (Objects.nonNull(userTeam)) {
            repository.saveUserTeam(careerId, userTeam);
        }
        List<Map<String, String>> teams = tables.get(TEAMS);
        List<Map<String, String>> formations = tables.get(FORMATIONS);
        if (Objects.nonNull(teams) && !teams.isEmpty()) {
            repository.replaceTeamInfo(careerId, toTeamInfo(teams, formations, eaToBase));
        }
        int forms = applyForm(careerId, tables.get(LINKS), eaToBase);
        log.info("Match profile imported careerId : {} userTeam : {} formations : {} forms : {}", careerId, userTeam,
                Objects.isNull(formations) ? 0 : formations.size(), forms);
    }

    private static List<TeamMatchInfo> toTeamInfo(List<Map<String, String>> teams, List<Map<String, String>> formations, Map<Long, Long> eaToBase) {
        Map<Integer, Map<String, String>> formationByTeam = new HashMap<>();
        if (Objects.nonNull(formations)) {
            formations.forEach((row) -> {
                Integer team = toInt(row.get("teamid"));
                if (Objects.nonNull(team)) {
                    formationByTeam.put(team, row);
                }
            });
        }
        List<TeamMatchInfo> out = new ArrayList<>();
        for (Map<String, String> row : teams) {
            Integer teamId = toInt(row.get("teamid"));
            if (Objects.isNull(teamId)) {
                continue;
            }
            Map<String, String> formation = formationByTeam.get(teamId);
            out.add(new TeamMatchInfo(teamId,
                    Objects.isNull(formation) ? null : formation.get("formationname"),
                    Objects.isNull(formation) ? null : readSlots(formation, "position%d"),
                    Objects.isNull(formation) ? null : readSlots(formation, "pos%drole"),
                    findPlayer(row, "captainid", eaToBase), findPlayer(row, "penaltytakerid", eaToBase), findPlayer(row, "freekicktakerid", eaToBase),
                    findPlayer(row, "leftcornerkicktakerid", eaToBase), findPlayer(row, "rightcornerkicktakerid", eaToBase),
                    toInt(row.get("matchdayoverallrating")), toInt(row.get("matchdayattackrating")),
                    toInt(row.get("matchdaymidfieldrating")), toInt(row.get("matchdaydefenserating"))));
        }
        return out;
    }

    private int applyForm(long careerId, List<Map<String, String>> links, Map<Long, Long> eaToBase) {
        if (Objects.isNull(links) || links.isEmpty() || !links.get(0).containsKey("form")) {
            return 0;
        }
        List<long[]> rows = new ArrayList<>();
        for (Map<String, String> link : links) {
            Long playerId = eaToBase.get(toLong(link.get("playerid")));
            Integer team = toInt(link.get("teamid"));
            Integer form = toInt(link.get("form"));
            if (Objects.nonNull(playerId) && Objects.nonNull(team) && Objects.nonNull(form)) {
                rows.add(new long[] {playerId, team, form});
            }
        }
        return repository.saveForm(careerId, rows);
    }

    private static List<Integer> readSlots(Map<String, String> formation, String pattern) {
        List<Integer> out = new ArrayList<>(FORMATION_SLOTS);
        for (int i = 0; i < FORMATION_SLOTS; i++) {
            out.add(toInt(formation.get(String.format(pattern, i))));
        }
        return out;
    }

    private static Long findPlayer(Map<String, String> row, String column, Map<Long, Long> eaToBase) {
        Long ea = toLong(row.get(column));
        return Objects.isNull(ea) ? null : eaToBase.get(ea);
    }

    private static Integer toInt(String value) {
        Long parsed = toLong(value);
        return Objects.isNull(parsed) ? null : parsed.intValue();
    }

    private static Long toLong(String value) {
        if (Objects.isNull(value) || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
