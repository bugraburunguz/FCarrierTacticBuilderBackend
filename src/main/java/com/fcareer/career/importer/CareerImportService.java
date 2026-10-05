package com.fcareer.career.importer;

import com.fcareer.billing.service.EntitlementService;
import com.fcareer.career.entity.CareerEntity;
import com.fcareer.career.model.response.ImportSummaryResponse;
import com.fcareer.career.service.CareerService;
import com.fcareer.common.exception.ImportParseException;
import com.fcareer.ingestion.base.BaseUploads;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.fcareer.career.importer.CsvStream.toInt;
import static com.fcareer.career.importer.CsvStream.toLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class CareerImportService {

    private static final Set<Integer> FREE_AGENT_TEAM_IDS = Set.of(111592, 131368);
    private static final Set<String> TEAM_COLUMNS = Set.of("teamname", "overallrating", "attackrating", "midfieldrating",
            "defenserating", "buildupplay", "defensivedepth", "youthdevelopment", "domesticprestige",
            "internationalprestige", "popularity", "clubworth", "profitability", "rivalteam", "gender");
    private static final int BATCH = 1000;

    private final NamedParameterJdbcTemplate jdbc;
    private final CareerService careerService;
    private final EntitlementService entitlementService;

    @Transactional
    public ImportSummaryResponse importData(Long userId, Long careerId, MultipartFile logFile, MultipartFile players, MultipartFile teams,
                                            MultipartFile links, MultipartFile leagueLinks, MultipartFile leagues) {
        entitlementService.requirePremium(userId);
        CareerEntity career = careerService.requireOwned(userId, careerId);

        Map<String, MultipartFile> csvs = new HashMap<>();
        csvs.put("players", players);
        csvs.put("teams", teams);
        csvs.put("teamplayerlinks", links);
        csvs.put("leagueteamlinks", leagueLinks);
        csvs.put("leagues", leagues);
        Map<String, List<Map<String, String>>> tables = BaseUploads.toTables(logFile, csvs);
        Map<Integer, String> leagueNames = new HashMap<>();
        Set<Integer> internationalLeagues = new HashSet<>();
        readLeagues(tables.get("leagues"), leagueNames, internationalLeagues);
        Map<Integer, Integer> teamLeague = readLeagueLinks(tables.get("leagueteamlinks"));
        Map<Integer, Map<String, String>> teamRows = readTeams(tables.get("teams"));
        Set<Integer> nationalTeams = new HashSet<>();
        teamLeague.forEach((team, league) -> {
            if (internationalLeagues.contains(league)) {
                nationalTeams.add(team);
            }
        });
        Map<Long, Integer> playerTeam = readLinks(tables.get("teamplayerlinks"), nationalTeams);
        Map<Long, Long> eaToBase = eaIdMap();
        Map<Long, int[]> ratings = new HashMap<>();
        readPlayers(tables.get("players"), ratings);

        List<SqlParameterSource> playerRows = new ArrayList<>();
        int unmatched = 0;
        int freeAgents = 0;
        for (Map.Entry<Long, int[]> entry : ratings.entrySet()) {
            Long baseId = eaToBase.get(entry.getKey());
            Integer teamId = playerTeam.get(entry.getKey());
            if (Objects.isNull(baseId) || Objects.isNull(teamId)) {
                unmatched++;
                continue;
            }
            boolean freeAgent = FREE_AGENT_TEAM_IDS.contains(teamId);
            if (freeAgent) {
                freeAgents++;
            }
            playerRows.add(new MapSqlParameterSource()
                    .addValue("career", careerId).addValue("base", baseId)
                    .addValue("overall", entry.getValue()[0]).addValue("potential", entry.getValue()[1])
                    .addValue("team", teamId).addValue("free", freeAgent));
        }
        if (playerRows.isEmpty()) {
            throw new ImportParseException("no player matched the catalog");
        }

        jdbc.update("DELETE FROM career_player_data WHERE career_id = :c", Map.of("c", careerId));
        jdbc.update("DELETE FROM career_team WHERE career_id = :c", Map.of("c", careerId));
        batch("""
                INSERT INTO career_player_data (career_id, base_player_id, overall, potential, team_id, free_agent)
                VALUES (:career, :base, :overall, :potential, :team, :free)""", playerRows);

        List<SqlParameterSource> teamParams = new ArrayList<>();
        for (Map.Entry<Integer, Map<String, String>> entry : teamRows.entrySet()) {
            Integer teamId = entry.getKey();
            if (nationalTeams.contains(teamId)) {
                continue;
            }
            Map<String, String> row = entry.getValue();
            teamParams.add(new MapSqlParameterSource()
                    .addValue("career", careerId).addValue("team", teamId).addValue("name", row.get("teamname"))
                    .addValue("league", leagueNames.get(teamLeague.get(teamId)))
                    .addValue("gender", toInt(row.get("gender")))
                    .addValue("pool", FREE_AGENT_TEAM_IDS.contains(teamId))
                    .addValue("overall", toInt(row.get("overallrating"))).addValue("attack", toInt(row.get("attackrating")))
                    .addValue("midfield", toInt(row.get("midfieldrating"))).addValue("defence", toInt(row.get("defenserating")))
                    .addValue("buildUp", toInt(row.get("buildupplay"))).addValue("depth", toInt(row.get("defensivedepth")))
                    .addValue("youth", toInt(row.get("youthdevelopment"))).addValue("domestic", toInt(row.get("domesticprestige")))
                    .addValue("international", toInt(row.get("internationalprestige"))).addValue("popularity", toInt(row.get("popularity")))
                    .addValue("worth", toLong(row.get("clubworth"))).addValue("profit", toInt(row.get("profitability")))
                    .addValue("rival", toInt(row.get("rivalteam"))));
        }
        batch("""
                INSERT INTO career_team (career_id, team_id, name, league_name, gender, free_agent_pool, overall, attack, midfield,
                  defence, build_up_play, defensive_depth, youth_development, domestic_prestige, international_prestige,
                  popularity, club_worth, profitability, rival_team_id)
                VALUES (:career, :team, :name, :league, :gender, :pool, :overall, :attack, :midfield, :defence, :buildUp, :depth,
                  :youth, :domestic, :international, :popularity, :worth, :profit, :rival)""", teamParams);

        jdbc.update("""
                INSERT INTO career_import (career_id, imported_at, player_count, team_count) VALUES (:c, now(), :p, :t)
                ON CONFLICT (career_id) DO UPDATE SET imported_at = now(), player_count = :p, team_count = :t""",
                new MapSqlParameterSource().addValue("c", careerId).addValue("p", playerRows.size()).addValue("t", teamParams.size()));
        jdbc.update("""
                UPDATE user_player_state s SET dynamic_potential = d.potential, updated_at = now()
                FROM career_player_data d
                WHERE s.career_id = :c AND d.career_id = s.career_id AND d.base_player_id = s.base_player_id""",
                Map.of("c", careerId));

        List<Map<String, Object>> guess = jdbc.queryForList("""
                SELECT t.team_id, t.name FROM career_team t JOIN club c ON c.id = :club
                WHERE t.career_id = :c AND NOT t.free_agent_pool AND lower(t.name) = lower(c.name) LIMIT 1""",
                new MapSqlParameterSource().addValue("c", careerId).addValue("club", career.getClubId()));
        log.info("Career import done careerId : {} players : {} teams : {} freeAgents : {} unmatched : {}",
                careerId, playerRows.size(), teamParams.size(), freeAgents, unmatched);
        return ImportSummaryResponse.builder()
                .players(playerRows.size()).unmatchedPlayers(unmatched).teams(teamParams.size()).freeAgents(freeAgents)
                .guessedTeamId(guess.isEmpty() ? null : ((Number) guess.get(0).get("team_id")).intValue())
                .guessedTeamName(guess.isEmpty() ? null : (String) guess.get(0).get("name"))
                .build();
    }

    @Transactional
    public int syncSquad(Long userId, Long careerId, Integer teamId) {
        entitlementService.requirePremium(userId);
        CareerEntity career = careerService.requireOwned(userId, careerId);
        Integer count = jdbc.queryForObject("SELECT count(*) FROM career_player_data WHERE career_id = :c AND team_id = :t",
                Map.of("c", careerId, "t", teamId), Integer.class);
        if (Objects.isNull(count) || count == 0) {
            throw new ImportParseException("team has no imported players");
        }
        jdbc.update("DELETE FROM user_player_state WHERE career_id = :c", Map.of("c", careerId));
        jdbc.update("""
                INSERT INTO user_player_state (career_id, base_player_id, club_id, dynamic_potential, on_loan, acquired_at, updated_at)
                SELECT :c, d.base_player_id, :club, d.potential, false, now(), now()
                FROM career_player_data d WHERE d.career_id = :c AND d.team_id = :t""",
                new MapSqlParameterSource().addValue("c", careerId).addValue("t", teamId).addValue("club", career.getClubId()));
        log.info("Squad synced from import careerId : {} teamId : {} players : {}", careerId, teamId, count);
        return count;
    }

    private void batch(String sql, List<SqlParameterSource> rows) {
        for (int from = 0; from < rows.size(); from += BATCH) {
            List<SqlParameterSource> part = rows.subList(from, Math.min(from + BATCH, rows.size()));
            jdbc.batchUpdate(sql, part.toArray(new SqlParameterSource[0]));
        }
    }

    private Map<Long, Long> eaIdMap() {
        Map<Long, Long> map = new HashMap<>();
        jdbc.query("SELECT id, ea_resource_id FROM base_player WHERE ea_resource_id IS NOT NULL",
                rs -> {
                    map.put(rs.getLong("ea_resource_id"), rs.getLong("id"));
                });
        return map;
    }

    private void readPlayers(List<Map<String, String>> rows, Map<Long, int[]> ratings) {
        for (Map<String, String> row : required(rows, "players")) {
            Long id = toLong(row.get("playerid"));
            Integer overall = toInt(row.get("overallrating"));
            Integer potential = toInt(row.get("potential"));
            if (Objects.nonNull(id) && Objects.nonNull(overall) && Objects.nonNull(potential)) {
                ratings.put(id, new int[]{overall, potential});
            }
        }
    }

    private Map<Integer, Map<String, String>> readTeams(List<Map<String, String>> rows) {
        Map<Integer, Map<String, String>> teams = new HashMap<>();
        for (Map<String, String> row : required(rows, "teams")) {
            Integer id = toInt(row.get("teamid"));
            if (Objects.nonNull(id) && Objects.nonNull(row.get("teamname"))) {
                teams.put(id, row);
            }
        }
        return teams;
    }

    private Map<Long, Integer> readLinks(List<Map<String, String>> rows, Set<Integer> nationalTeams) {
        Map<Long, Integer> links = new HashMap<>();
        for (Map<String, String> row : required(rows, "teamplayerlinks")) {
            Long player = toLong(row.get("playerid"));
            Integer team = toInt(row.get("teamid"));
            if (Objects.isNull(player) || Objects.isNull(team) || nationalTeams.contains(team)) {
                continue;
            }
            Integer existing = links.get(player);
            if (Objects.isNull(existing) || (FREE_AGENT_TEAM_IDS.contains(existing) && !FREE_AGENT_TEAM_IDS.contains(team))) {
                links.put(player, team);
            }
        }
        return links;
    }

    private Map<Integer, Integer> readLeagueLinks(List<Map<String, String>> rows) {
        Map<Integer, Integer> links = new HashMap<>();
        if (Objects.isNull(rows)) {
            return links;
        }
        for (Map<String, String> row : rows) {
            Integer team = toInt(row.get("teamid"));
            Integer league = toInt(row.get("leagueid"));
            if (Objects.nonNull(team) && Objects.nonNull(league)) {
                links.put(team, league);
            }
        }
        return links;
    }

    private void readLeagues(List<Map<String, String>> rows, Map<Integer, String> names, Set<Integer> international) {
        if (Objects.isNull(rows)) {
            return;
        }
        for (Map<String, String> row : rows) {
            Integer id = toInt(row.get("leagueid"));
            if (Objects.isNull(id)) {
                continue;
            }
            names.put(id, row.get("leaguename"));
            if (Objects.equals(toInt(row.get("isinternationalleague")), 1)) {
                international.add(id);
            }
        }
    }

    private List<Map<String, String>> required(List<Map<String, String>> rows, String table) {
        if (Objects.isNull(rows) || rows.isEmpty()) {
            throw new ImportParseException("table " + table + " missing");
        }
        return rows;
    }
}
