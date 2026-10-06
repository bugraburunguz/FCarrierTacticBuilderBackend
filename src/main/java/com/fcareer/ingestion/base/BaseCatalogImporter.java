package com.fcareer.ingestion.base;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.common.exception.ImportParseException;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.ingestion.mapper.AcceleRateDeriver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.fcareer.career.importer.CsvStream.toInt;
import static com.fcareer.career.importer.CsvStream.toLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class BaseCatalogImporter {

    public static final Set<Integer> FREE_AGENT_TEAM_IDS = Set.of(111592, 131368);
    private static final String FREE_AGENTS = "Free Agents";
    private static final long CAREER_CREATED_MIN_ID = 400_000L;
    private static final String PLACEHOLDER_TEAM_PREFIX = "*TeamName";
    private static final List<String> NON_CATALOG_LEAGUES = List.of("Custom", "FIFA Twist", "5v5", "Rest of World Men");
    private static final int BATCH = 1000;
    private static final int MIN_OVERALL = 1;
    private static final double NATIONALITY_CONFIDENCE = 0.6;

    public record BaseImportResult(int playersInFile, int existing, int created, int skippedNoTeam, int skippedNoPosition,
                                   int nationalitiesCreated, int clubsCreated, int leaguesCreated, int updated) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final AcceleRateDeriver acceleRateDeriver;
    private final ObjectMapper objectMapper;

    @Transactional
    public BaseImportResult importMissing(Map<String, List<Map<String, String>>> tables, short gameVersionId, boolean overwriteExisting,
                                         boolean createMissing) {
        Context ctx = buildContext(tables);
        List<Map<String, String>> players = ctx.players();
        Map<Long, Integer> playerTeam = ctx.playerTeam();
        Map<Integer, String> names = ctx.names();
        Map<Long, Long> existing = ctx.existing();
        NationalityResolver nationalities = ctx.nationalities();
        ClubResolver clubs = ctx.clubs();
        Set<Integer> excludedTeams = ctx.excludedTeams();
        Map<Long, String> edited = ctx.edited();

        List<SqlParameterSource> basePlayers = new ArrayList<>();
        Map<Long, Map<String, String>> pending = new LinkedHashMap<>();
        int already = 0;
        List<SqlParameterSource> baseUpdates = new ArrayList<>();
        List<SqlParameterSource> ratingUpdates = new ArrayList<>();
        int noTeam = 0;
        int noPosition = 0;
        for (Map<String, String> row : players) {
            Long eaId = toLong(row.get("playerid"));
            Integer overall = toInt(row.get("overallrating"));
            if (Objects.isNull(eaId) || Objects.isNull(overall) || overall < MIN_OVERALL) {
                continue;
            }
            if (existing.containsKey(eaId)) {
                already++;
                if (overwriteExisting && !positions(row).isEmpty()) {
                    Long baseId = existing.get(eaId);
                    baseUpdates.add(baseUpdateParams(baseId, row));
                    ratingUpdates.add(ratingParams(baseId, row, gameVersionId, null));
                }
                continue;
            }
            if (!createMissing || eaId >= CAREER_CREATED_MIN_ID) {
                continue;
            }
            Integer teamId = playerTeam.get(eaId);
            if (Objects.nonNull(teamId) && (excludedTeams.contains(teamId) || isPlaceholderTeam(clubs.teamName(teamId)))) {
                continue;
            }
            if (Objects.isNull(teamId)) {
                noTeam++;
                continue;
            }
            if (positions(row).isEmpty()) {
                noPosition++;
                continue;
            }
            Integer birth = toInt(row.get("birthdate"));
            basePlayers.add(new MapSqlParameterSource()
                    .addValue("ea", eaId)
                    .addValue("name", nameOf(row, eaId, names, edited))
                    .addValue("dob", Objects.isNull(birth) ? null : java.sql.Date.valueOf(LocalDate.ofEpochDay(birth + DumpMappings.BIRTHDATE_EPOCH_OFFSET)))
                    .addValue("nat", nationalities.idFor(toInt(row.get("nationality"))), java.sql.Types.INTEGER)
                    .addValue("height", positiveOrNull(toInt(row.get("height"))), java.sql.Types.SMALLINT)
                    .addValue("weight", positiveOrNull(toInt(row.get("weight"))), java.sql.Types.SMALLINT)
                    .addValue("foot", foot(toInt(row.get("preferredfoot"))))
                    .addValue("gender", Objects.equals(toInt(row.get("gender")), 1) ? 1 : 0));
            pending.put(eaId, row);
        }
        batch("""
                INSERT INTO base_player (ea_resource_id, full_name, dob, nationality_id, height_cm, weight_kg, preferred_foot, gender)
                VALUES (:ea, :name, :dob, :nat, :height, :weight, :foot, :gender)""", basePlayers);

        batch(BASE_UPDATE_SQL, baseUpdates);
        batch(RATING_UPDATE_SQL, ratingUpdates);
        renameNameless(players, names, edited);

        Map<Long, Long> created = idsByEaId(pending.keySet());
        List<SqlParameterSource> ratings = new ArrayList<>();
        for (Map.Entry<Long, Map<String, String>> entry : pending.entrySet()) {
            Long baseId = created.get(entry.getKey());
            if (Objects.nonNull(baseId)) {
                ratings.add(ratingParams(baseId, entry.getValue(), gameVersionId, clubs.idFor(playerTeam.get(entry.getKey()))));
            }
        }
        batch(RATING_SQL, ratings);

        log.info("Base catalog import players : {} created : {} existing : {} noTeam : {} noPosition : {}",
                players.size(), ratings.size(), already, noTeam, noPosition);
        return new BaseImportResult(players.size(), already, ratings.size(), noTeam, noPosition,
                nationalities.created(), clubs.createdClubs(), clubs.createdLeagues(), ratingUpdates.size());
    }


    private record Context(List<Map<String, String>> players, Map<Long, Integer> playerTeam, Map<Integer, String> names,
                           Map<Long, Long> existing, NationalityResolver nationalities, ClubResolver clubs, Set<Integer> excludedTeams,
                           Map<Long, String> edited) {
    }

    private Context buildContext(Map<String, List<Map<String, String>>> tables) {
        List<Map<String, String>> players = require(tables, "players");
        List<Map<String, String>> teams = require(tables, "teams");
        List<Map<String, String>> links = require(tables, "teamplayerlinks");
        List<Map<String, String>> leagueLinks = tables.getOrDefault("leagueteamlinks", List.of());
        List<Map<String, String>> leagues = tables.getOrDefault("leagues", List.of());

        Map<Integer, String> leagueNames = new HashMap<>();
        Set<Integer> international = new HashSet<>();
        for (Map<String, String> row : leagues) {
            Integer id = toInt(row.get("leagueid"));
            if (Objects.nonNull(id)) {
                leagueNames.put(id, row.get("leaguename"));
                if (Objects.equals(toInt(row.get("isinternationalleague")), 1)) {
                    international.add(id);
                }
            }
        }
        Map<Integer, Integer> teamLeague = new HashMap<>();
        for (Map<String, String> row : leagueLinks) {
            Integer team = toInt(row.get("teamid"));
            Integer league = toInt(row.get("leagueid"));
            if (Objects.nonNull(team) && Objects.nonNull(league)) {
                teamLeague.put(team, league);
            }
        }
        Set<Integer> nationalTeams = new HashSet<>();
        teamLeague.forEach((team, league) -> {
            if (international.contains(league)) {
                nationalTeams.add(team);
            }
        });
        Map<Integer, Map<String, String>> teamRows = new HashMap<>();
        for (Map<String, String> row : teams) {
            Integer id = toInt(row.get("teamid"));
            if (Objects.nonNull(id)) {
                teamRows.put(id, row);
            }
        }
        Map<Long, Integer> playerTeam = playerTeams(links, nationalTeams);

        List<Map<String, String>> nameRows = new ArrayList<>(tables.getOrDefault("playernames", List.of()));
        nameRows.addAll(tables.getOrDefault("dcplayernames", List.of()));
        Map<Integer, String> names = playerNames(nameRows);
        Map<Long, Long> existing = existingPlayers();
        NationalityResolver nationalities = new NationalityResolver(players, existing);
        ClubResolver clubs = new ClubResolver(teamRows, teamLeague, leagueNames);
        Set<Integer> excludedTeams = new HashSet<>();
        teamLeague.forEach((team, league) -> {
            String leagueName = Objects.requireNonNullElse(leagueNames.get(league), "");
            if (NON_CATALOG_LEAGUES.stream().anyMatch(marker -> leagueName.contains(marker))) {
                excludedTeams.add(team);
            }
        });
        return new Context(players, playerTeam, names, existing, nationalities, clubs, excludedTeams,
                editedNames(tables.getOrDefault("editedplayernames", List.of())));
    }

    /**
     * Kariyerde üretilmiş (katalogda karşılığı olmayan) oyuncuları o kariyere ait oyuncu olarak oluşturur/günceller.
     * Dönen harita: dump playerid → base_player.id.
     */
    @Transactional
    public Map<Long, Long> upsertCareerPlayers(Long careerId, Map<String, List<Map<String, String>>> tables, short gameVersionId) {
        Context ctx = buildContext(tables);
        Map<Long, Long> known = careerPlayers(careerId);
        List<SqlParameterSource> inserts = new ArrayList<>();
        List<SqlParameterSource> baseUpdates = new ArrayList<>();
        List<SqlParameterSource> ratingUpdates = new ArrayList<>();
        Map<Long, Map<String, String>> fresh = new LinkedHashMap<>();
        for (Map<String, String> row : ctx.players()) {
            Long sourceId = toLong(row.get("playerid"));
            Integer overall = toInt(row.get("overallrating"));
            if (Objects.isNull(sourceId) || Objects.isNull(overall) || overall < MIN_OVERALL || ctx.existing().containsKey(sourceId)
                    || Objects.isNull(ctx.playerTeam().get(sourceId)) || positions(row).isEmpty()) {
                continue;
            }
            if (known.containsKey(sourceId)) {
                baseUpdates.add(baseUpdateParams(known.get(sourceId), row));
                ratingUpdates.add(ratingParams(known.get(sourceId), row, gameVersionId, ctx.clubs().idFor(ctx.playerTeam().get(sourceId))));
                continue;
            }
            Integer birth = toInt(row.get("birthdate"));
            inserts.add(new MapSqlParameterSource().addValue("career", careerId).addValue("source", sourceId)
                    .addValue("name", nameOf(row, sourceId, ctx.names(), ctx.edited()))
                    .addValue("dob", Objects.isNull(birth) ? null : java.sql.Date.valueOf(LocalDate.ofEpochDay(birth + DumpMappings.BIRTHDATE_EPOCH_OFFSET)))
                    .addValue("nat", ctx.nationalities().idFor(toInt(row.get("nationality"))), java.sql.Types.INTEGER)
                    .addValue("height", positiveOrNull(toInt(row.get("height"))), java.sql.Types.SMALLINT)
                    .addValue("weight", positiveOrNull(toInt(row.get("weight"))), java.sql.Types.SMALLINT)
                    .addValue("foot", foot(toInt(row.get("preferredfoot"))))
                    .addValue("gender", Objects.equals(toInt(row.get("gender")), 1) ? 1 : 0));
            fresh.put(sourceId, row);
        }
        batch("""
                INSERT INTO base_player (owner_career_id, career_source_id, full_name, dob, nationality_id, height_cm, weight_kg, preferred_foot, gender)
                VALUES (:career, :source, :name, :dob, :nat, :height, :weight, :foot, :gender)""", inserts);
        batch(BASE_UPDATE_SQL, baseUpdates);
        batch(RATING_UPDATE_SQL_WITH_CLUB, ratingUpdates);
        Map<Long, Long> all = careerPlayers(careerId);
        List<SqlParameterSource> ratings = new ArrayList<>();
        fresh.forEach((sourceId, row) -> ratings.add(ratingParams(all.get(sourceId), row, gameVersionId,
                ctx.clubs().idFor(ctx.playerTeam().get(sourceId)))));
        batch(RATING_SQL, ratings);
        log.info("Career generated players careerId : {} created : {} updated : {}", careerId, ratings.size(), ratingUpdates.size());
        return all;
    }

    private void renameNameless(List<Map<String, String>> players, Map<Integer, String> names, Map<Long, String> edited) {
        Map<Long, Long> nameless = new HashMap<>();
        jdbc.query("SELECT id, ea_resource_id FROM base_player WHERE owner_career_id IS NULL AND full_name LIKE 'Oyuncu #%'", rs -> {
            nameless.put(rs.getLong("ea_resource_id"), rs.getLong("id"));
        });
        List<SqlParameterSource> renames = new ArrayList<>();
        for (Map<String, String> row : players) {
            Long eaId = toLong(row.get("playerid"));
            Long baseId = nameless.get(eaId);
            if (Objects.isNull(baseId)) {
                continue;
            }
            String name = nameOf(row, eaId, names, edited);
            if (!name.startsWith("Oyuncu #")) {
                renames.add(new MapSqlParameterSource().addValue("id", baseId).addValue("name", name));
            }
        }
        batch("UPDATE base_player SET full_name = :name WHERE id = :id", renames);
        log.info("Renamed nameless players : {}", renames.size());
    }

    private Map<Long, Long> careerPlayers(Long careerId) {
        Map<Long, Long> map = new HashMap<>();
        jdbc.query("SELECT id, career_source_id FROM base_player WHERE owner_career_id = :c",
                new MapSqlParameterSource("c", careerId), rs -> {
                    map.put(rs.getLong("career_source_id"), rs.getLong("id"));
                });
        return map;
    }

    private static final String RATING_SQL;
    private static final String RATING_UPDATE_SQL_WITH_CLUB;
    private static final String RATING_UPDATE_SQL;
    private static final String BASE_UPDATE_SQL = """
            UPDATE base_player SET dob = :dob, height_cm = :height, weight_kg = :weight, preferred_foot = :foot, gender = :gender
            WHERE id = :base""";

    static {
        StringBuilder columns = new StringBuilder("base_player_id, game_version_id, club_id, overall, positions, accelerate, weak_foot, "
                + "skill_moves, intl_reputation, playstyles, run_style1, run_style2");
        StringBuilder values = new StringBuilder(":base, :gv, :club, :overall, CAST(string_to_array(:positions, ',') AS text[]), :accelerate, "
                + ":weakFoot, :skillMoves, :intl, CAST(:playstyles AS jsonb), :run1, :run2");
        for (Attr attr : DumpMappings.ATTRIBUTE_COLUMNS.values()) {
            columns.append(", ").append(attr.getColumn());
            values.append(", :").append(attr.getColumn());
        }
        RATING_SQL = "INSERT INTO player_rating (" + columns + ") VALUES (" + values + ")";
        StringBuilder sets = new StringBuilder("overall = :overall, positions = CAST(string_to_array(:positions, ',') AS text[]), "
                + "accelerate = :accelerate, weak_foot = :weakFoot, skill_moves = :skillMoves, playstyles = CAST(:playstyles AS jsonb), "
                + "run_style1 = :run1, run_style2 = :run2");
        for (Attr attr : DumpMappings.ATTRIBUTE_COLUMNS.values()) {
            sets.append(", ").append(attr.getColumn()).append(" = :").append(attr.getColumn());
        }
        RATING_UPDATE_SQL = "UPDATE player_rating SET " + sets + " WHERE base_player_id = :base AND game_version_id = :gv";
        RATING_UPDATE_SQL_WITH_CLUB = "UPDATE player_rating SET club_id = :club, " + sets + " WHERE base_player_id = :base AND game_version_id = :gv";
    }

    private SqlParameterSource baseUpdateParams(Long baseId, Map<String, String> row) {
        Integer birth = toInt(row.get("birthdate"));
        return new MapSqlParameterSource().addValue("base", baseId)
                .addValue("dob", Objects.isNull(birth) ? null : java.sql.Date.valueOf(LocalDate.ofEpochDay(birth + DumpMappings.BIRTHDATE_EPOCH_OFFSET)))
                .addValue("height", positiveOrNull(toInt(row.get("height"))), java.sql.Types.SMALLINT)
                .addValue("weight", positiveOrNull(toInt(row.get("weight"))), java.sql.Types.SMALLINT)
                .addValue("foot", foot(toInt(row.get("preferredfoot"))))
                .addValue("gender", Objects.equals(toInt(row.get("gender")), 1) ? 1 : 0);
    }

    private SqlParameterSource ratingParams(Long baseId, Map<String, String> row, short gameVersionId, Integer clubId) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        Map<Attr, Integer> attrs = new EnumMap<>(Attr.class);
        DumpMappings.ATTRIBUTE_COLUMNS.forEach((column, attr) -> {
            Integer value = toInt(row.get(column));
            int safe = Objects.isNull(value) ? 0 : value;
            attrs.put(attr, safe);
            params.addValue(attr.getColumn(), safe, java.sql.Types.SMALLINT);
        });
        boolean women = Objects.equals(toInt(row.get("gender")), 1);
        String accelerate = acceleRateDeriver.derive(positiveOrNull(toInt(row.get("height"))), women ? 1 : 0, attrs)
                .map(Enum::name).orElse(null);
        Integer skill = toInt(row.get("skillmoves"));
        params.addValue("base", baseId).addValue("gv", gameVersionId).addValue("club", clubId, java.sql.Types.INTEGER)
                .addValue("overall", toInt(row.get("overallrating")), java.sql.Types.SMALLINT)
                .addValue("positions", String.join(",", positions(row)))
                .addValue("accelerate", accelerate)
                .addValue("weakFoot", toInt(row.get("weakfootabilitytypecode")), java.sql.Types.SMALLINT)
                .addValue("skillMoves", Objects.isNull(skill) ? null : skill + DumpMappings.SKILL_MOVES_OFFSET, java.sql.Types.SMALLINT)
                .addValue("intl", toInt(row.get("internationalrep")), java.sql.Types.SMALLINT)
                .addValue("playstyles", playstylesJson(row))
                .addValue("run1", Objects.requireNonNullElse(toInt(row.get("runningcode1")), 0))
                .addValue("run2", Objects.requireNonNullElse(toInt(row.get("runningcode2")), 0));
        return params;
    }

    private List<String> positions(Map<String, String> row) {
        Set<String> positions = new LinkedHashSet<>();
        for (int i = 1; i <= DumpMappings.POSITION_SLOTS; i++) {
            Integer code = toInt(row.get("preferredposition" + i));
            if (Objects.nonNull(code) && code != DumpMappings.NO_POSITION && DumpMappings.POSITION_BY_CODE.containsKey(code)) {
                positions.add(DumpMappings.POSITION_BY_CODE.get(code));
            }
        }
        return new ArrayList<>(positions);
    }

    private String playstylesJson(Map<String, String> row) {
        Map<String, Integer> styles = new LinkedHashMap<>();
        collectStyles(styles, DumpMappings.OUTFIELD_PLAYSTYLES, toLong(row.get("trait1")), toLong(row.get("icontrait1")));
        collectStyles(styles, DumpMappings.GOALKEEPER_PLAYSTYLES, toLong(row.get("trait2")), toLong(row.get("icontrait2")));
        try {
            return objectMapper.writeValueAsString(styles);
        } catch (JsonProcessingException e) {
            throw new ImportParseException("playstyles");
        }
    }

    private void collectStyles(Map<String, Integer> styles, String[] names, Long traits, Long plus) {
        for (int bit = 0; bit < names.length; bit++) {
            if (Objects.nonNull(plus) && ((plus >> bit) & 1L) == 1L) {
                styles.put(names[bit], 2);
            } else if (Objects.nonNull(traits) && ((traits >> bit) & 1L) == 1L) {
                styles.put(names[bit], 1);
            }
        }
    }

    private Map<Long, Integer> playerTeams(List<Map<String, String>> links, Set<Integer> nationalTeams) {
        Map<Long, Integer> result = new HashMap<>();
        for (Map<String, String> row : links) {
            Long player = toLong(row.get("playerid"));
            Integer team = toInt(row.get("teamid"));
            if (Objects.isNull(player) || Objects.isNull(team) || nationalTeams.contains(team)) {
                continue;
            }
            Integer current = result.get(player);
            if (Objects.isNull(current) || (FREE_AGENT_TEAM_IDS.contains(current) && !FREE_AGENT_TEAM_IDS.contains(team))) {
                result.put(player, team);
            }
        }
        return result;
    }

    private Map<Long, Long> existingPlayers() {
        Map<Long, Long> map = new HashMap<>();
        jdbc.query("SELECT id, ea_resource_id FROM base_player WHERE ea_resource_id IS NOT NULL",
                rs -> {
                    map.put(rs.getLong("ea_resource_id"), rs.getLong("id"));
                });
        return map;
    }

    private Map<Long, Long> idsByEaId(Set<Long> eaIds) {
        Map<Long, Long> map = new HashMap<>();
        List<Long> all = new ArrayList<>(eaIds);
        for (int from = 0; from < all.size(); from += BATCH) {
            List<Long> part = all.subList(from, Math.min(from + BATCH, all.size()));
            jdbc.query("SELECT id, ea_resource_id FROM base_player WHERE ea_resource_id IN (:ids)", Map.of("ids", part),
                    rs -> {
                        map.put(rs.getLong("ea_resource_id"), rs.getLong("id"));
                    });
        }
        return map;
    }

    private void batch(String sql, List<SqlParameterSource> rows) {
        for (int from = 0; from < rows.size(); from += BATCH) {
            List<SqlParameterSource> part = rows.subList(from, Math.min(from + BATCH, rows.size()));
            jdbc.batchUpdate(sql, part.toArray(new SqlParameterSource[0]));
        }
    }

    private static List<Map<String, String>> require(Map<String, List<Map<String, String>>> tables, String name) {
        List<Map<String, String>> rows = tables.get(name);
        if (Objects.isNull(rows) || rows.isEmpty()) {
            throw new ImportParseException("table " + name + " missing");
        }
        return rows;
    }

    private static Map<Integer, String> playerNames(List<Map<String, String>> rows) {
        Map<Integer, String> names = new HashMap<>();
        for (Map<String, String> row : rows) {
            Integer id = toInt(row.get("nameid"));
            String name = row.get("name");
            if (Objects.nonNull(id) && Objects.nonNull(name) && !name.isBlank()) {
                names.put(id, name.trim());
            }
        }
        return names;
    }

    private static boolean isPlaceholderTeam(String name) {
        return Objects.nonNull(name) && name.startsWith(PLACEHOLDER_TEAM_PREFIX);
    }

    private static Map<Long, String> editedNames(List<Map<String, String>> rows) {
        Map<Long, String> names = new HashMap<>();
        for (Map<String, String> row : rows) {
            Long id = toLong(row.get("playerid"));
            String common = Objects.requireNonNullElse(row.get("commonname"), "").trim();
            String full = (Objects.requireNonNullElse(row.get("firstname"), "").trim() + " " + Objects.requireNonNullElse(row.get("surname"), "").trim()).trim();
            String name = common.isEmpty() ? full : common;
            if (Objects.nonNull(id) && !name.isEmpty()) {
                names.put(id, name);
            }
        }
        return names;
    }

    private static String nameOf(Map<String, String> row, Long eaId, Map<Integer, String> names, Map<Long, String> edited) {
        if (edited.containsKey(eaId)) {
            return edited.get(eaId);
        }
        String common = names.get(toInt(row.get("commonnameid")));
        if (Objects.nonNull(common)) {
            return common;
        }
        String first = names.getOrDefault(toInt(row.get("firstnameid")), "");
        String last = names.getOrDefault(toInt(row.get("lastnameid")), "");
        String full = (first + " " + last).trim();
        if (!full.isEmpty()) {
            return full;
        }
        String inline = row.get("name");
        return Objects.isNull(inline) || inline.isBlank() ? "Oyuncu #" + eaId : inline.trim();
    }

    private static Integer positiveOrNull(Integer value) {
        return Objects.nonNull(value) && value > 0 ? value : null;
    }

    private static String foot(Integer code) {
        if (Objects.equals(code, DumpMappings.RIGHT_FOOT)) {
            return "Right";
        }
        return Objects.equals(code, DumpMappings.LEFT_FOOT) ? "Left" : null;
    }

    /** Dump uyruk id'si → nationality tablosu; katalogla eşleşen oyunculardan çoğunluk oyuyla türetilir. */
    private final class NationalityResolver {
        private final Map<Integer, Integer> byDumpId = new HashMap<>();
        private final Map<String, Integer> byName = new HashMap<>();
        private int created;

        NationalityResolver(List<Map<String, String>> players, Map<Long, Long> existing) {
            jdbc.query("SELECT id, name FROM nationality", rs -> {
                byName.put(rs.getString("name").toLowerCase(), rs.getInt("id"));
            });
            Map<Long, Integer> dumpNat = new HashMap<>();
            for (Map<String, String> row : players) {
                Long eaId = toLong(row.get("playerid"));
                Integer nat = toInt(row.get("nationality"));
                if (Objects.nonNull(eaId) && Objects.nonNull(nat) && existing.containsKey(eaId)) {
                    dumpNat.put(eaId, nat);
                }
            }
            Map<Long, Integer> catalogNat = new HashMap<>();
            jdbc.query("SELECT ea_resource_id, nationality_id FROM base_player WHERE ea_resource_id IS NOT NULL AND nationality_id IS NOT NULL",
                    rs -> {
                        catalogNat.put(rs.getLong("ea_resource_id"), rs.getInt("nationality_id"));
                    });
            Map<Integer, Map<Integer, Integer>> votes = new HashMap<>();
            dumpNat.forEach((eaId, nat) -> {
                Integer dbId = catalogNat.get(eaId);
                if (Objects.nonNull(dbId)) {
                    votes.computeIfAbsent(nat, k -> new HashMap<>()).merge(dbId, 1, Integer::sum);
                }
            });
            votes.forEach((nat, counts) -> {
                int total = counts.values().stream().mapToInt(Integer::intValue).sum();
                counts.entrySet().stream().max(Map.Entry.comparingByValue())
                        .filter(e -> e.getValue() >= total * NATIONALITY_CONFIDENCE)
                        .ifPresent(e -> byDumpId.put(nat, e.getKey()));
            });
        }

        Integer idFor(Integer dumpId) {
            if (Objects.isNull(dumpId)) {
                return null;
            }
            return byDumpId.computeIfAbsent(dumpId, id -> {
                String name = "Nationality #" + id;
                Integer existingId = byName.get(name.toLowerCase());
                if (Objects.nonNull(existingId)) {
                    return existingId;
                }
                Integer newId = jdbc.queryForObject("INSERT INTO nationality (name) VALUES (:n) RETURNING id", Map.of("n", name), Integer.class);
                byName.put(name.toLowerCase(), newId);
                created++;
                return newId;
            });
        }

        int created() {
            return created;
        }
    }

    /** Dump takımı → club tablosu: ad + cinsiyetle eşleşen kulüp, yoksa lig altında oluşturulur. */
    private final class ClubResolver {
        private final Map<Integer, Map<String, String>> teams;
        private final Map<Integer, Integer> teamLeague;
        private final Map<Integer, String> leagueNames;
        private final Map<Integer, Integer> cache = new HashMap<>();
        private final Map<String, Integer> leagueByName = new HashMap<>();
        private int createdClubs;
        private int createdLeagues;

        ClubResolver(Map<Integer, Map<String, String>> teams, Map<Integer, Integer> teamLeague, Map<Integer, String> leagueNames) {
            this.teams = teams;
            this.teamLeague = teamLeague;
            this.leagueNames = leagueNames;
            jdbc.query("SELECT id, name FROM league", rs -> {
                leagueByName.put(rs.getString("name").toLowerCase(), rs.getInt("id"));
            });
        }

        String teamName(Integer teamId) {
            Map<String, String> team = teams.get(teamId);
            return Objects.isNull(team) ? null : team.get("teamname");
        }

        Integer idFor(Integer teamId) {
            if (Objects.isNull(teamId)) {
                return null;
            }
            return cache.computeIfAbsent(teamId, this::resolve);
        }

        private Integer resolve(Integer teamId) {
            boolean freeAgent = FREE_AGENT_TEAM_IDS.contains(teamId);
            Map<String, String> team = teams.get(teamId);
            String name = freeAgent ? FREE_AGENTS : Objects.isNull(team) ? "Team " + teamId : team.get("teamname");
            Integer gender = Objects.isNull(team) ? null : toInt(team.get("gender"));
            List<Map<String, Object>> found = jdbc.queryForList(
                    "SELECT id FROM club WHERE lower(name) = lower(:n) AND (gender IS NULL OR :g IS NULL OR gender = :g) ORDER BY id LIMIT 1",
                    new MapSqlParameterSource().addValue("n", name).addValue("g", gender, java.sql.Types.INTEGER));
            if (!found.isEmpty()) {
                return ((Number) found.get(0).get("id")).intValue();
            }
            Integer leagueId = leagueFor(freeAgent ? FREE_AGENTS : leagueNames.get(teamLeague.get(teamId)));
            createdClubs++;
            return jdbc.queryForObject("INSERT INTO club (name, league_id, gender) VALUES (:n, :l, :g) RETURNING id",
                    new MapSqlParameterSource().addValue("n", name).addValue("l", leagueId, java.sql.Types.INTEGER)
                            .addValue("g", gender, java.sql.Types.INTEGER), Integer.class);
        }

        private Integer leagueFor(String name) {
            if (Objects.isNull(name) || name.isBlank()) {
                return null;
            }
            return leagueByName.computeIfAbsent(name.toLowerCase(), key -> {
                createdLeagues++;
                return jdbc.queryForObject("INSERT INTO league (name) VALUES (:n) RETURNING id", Map.of("n", name), Integer.class);
            });
        }

        int createdClubs() {
            return createdClubs;
        }

        int createdLeagues() {
            return createdLeagues;
        }
    }
}
