package com.fcareer.catalog.repository;

import com.fcareer.catalog.model.request.PlayerSearchRequest;
import com.fcareer.catalog.model.response.PlayerSummaryResponse;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.ingestion.base.CareerPlayerWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Period;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Oyuncu araması. Kaynak isteğe göre seçilir (E3-2): careerId varsa kariyerin tek doğruluk kaynağı (kariyer oyuncu tablosu), yoksa EA kataloğu.
 * İki kaynak aynı filtre/sıralama motorunu kullanır; kaynağa özgü SQL parçaları {@link SearchProfile} uygulamalarındadır.
 */
@Repository
@RequiredArgsConstructor
public class PlayerSearchRepository {

    private static final String AGE_SORT = "age";
    private static final String PRESCORE_SORT = "prescore";

    private final NamedParameterJdbcTemplate jdbc;
    private final com.fcareer.ut.service.FaceStatsService faceStatsService;
    private final CareerPlayerWriter careerPlayerWriter;
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    private SearchProfile profileOf(PlayerSearchRequest request) {
        if (Objects.isNull(request.getCareerId())) {
            return CatalogSearchProfile.INSTANCE;
        }
        careerPlayerWriter.ensureSeeded(request.getCareerId());
        return CareerSearchProfile.INSTANCE;
    }

    private static String select(SearchProfile p) {
        return "SELECT b.id AS id, b.ea_resource_id AS ea_id, b.full_name AS name, b.dob AS dob, b.gender AS gender, b.preferred_foot AS foot,\n"
                + p.overall() + " AS overall, " + p.positions() + " AS positions, " + p.weakFoot() + " AS weak_foot, " + p.skillMoves() + " AS skill_moves,\n"
                + p.clubId() + " AS club_id, " + p.clubName() + " AS club, " + p.leagueName() + " AS league, n.name AS nationality, " + p.accelerate() + " AS accelerate, "
                + p.runStyle() + " AS run_style,\n"
                + p.potential() + " AS potential, " + p.value() + " AS value_eur, " + p.estimated() + " AS estimated,\n"
                + p.freeAgentSelect() + " AS free_agent,\n"
                + p.gameDate() + " AS ref_date,\n"
                + p.faceOfficial() + " AS face_official,\n"
                + p.attrsJson() + " AS attrs_json\n";
    }

    /** Kariyerin oyun içi tarihi; kariyer yok ya da tarih girilmemişse boş (ENG-04). */
    public java.util.Optional<java.time.LocalDate> careerGameDate(Long careerId) {
        if (Objects.isNull(careerId)) {
            return java.util.Optional.empty();
        }
        return jdbc.queryForList("SELECT current_game_date FROM career WHERE id = :c AND current_game_date IS NOT NULL", Map.of("c", careerId), java.time.LocalDate.class).stream().findFirst();
    }

    public List<PlayerSummaryResponse> search(PlayerSearchRequest request) {
        SearchProfile profile = profileOf(request);
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = where(profile, request, params);
        params.addValue("limit", request.getSize());
        params.addValue("offset", (long) request.getPage() * request.getSize());
        String sql = select(profile) + profile.from() + where + " ORDER BY " + orderBy(profile, request, params) + " LIMIT :limit OFFSET :offset";
        return jdbc.query(sql, params, (rs, i) -> toSummary(rs));
    }

    public record LeagueQuality(int id, String name, double averageOverall) {
    }

    public List<LeagueQuality> leagueQuality(Short gameVersionId, Short gender, int minOverall, int minPlayers) {
        String sql = """
                SELECT l.id AS id, l.name AS name, AVG(r.overall) AS average
                  FROM player_rating r
                  JOIN base_player b ON b.id = r.base_player_id
                  JOIN club c ON c.id = r.club_id
                  JOIN league l ON l.id = c.league_id
                 WHERE b.owner_career_id IS NULL AND r.overall >= :minOverall
                   AND (CAST(:gameVersionId AS SMALLINT) IS NULL OR r.game_version_id = :gameVersionId)
                   AND (CAST(:gender AS SMALLINT) IS NULL OR b.gender = :gender)
                 GROUP BY l.id, l.name
                HAVING COUNT(*) >= :minPlayers
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("gameVersionId", gameVersionId)
                .addValue("gender", gender)
                .addValue("minOverall", minOverall)
                .addValue("minPlayers", minPlayers);
        return jdbc.query(sql, params, (rs, i) -> new LeagueQuality(rs.getInt("id"), rs.getString("name"), rs.getDouble("average")));
    }

    public long count(PlayerSearchRequest request) {
        SearchProfile profile = profileOf(request);
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = where(profile, request, params);
        Long total = jdbc.queryForObject("SELECT count(*)" + profile.from() + where, params, Long.class);
        return Objects.isNull(total) ? 0 : total;
    }

    private String where(SearchProfile p, PlayerSearchRequest request, MapSqlParameterSource params) {
        List<String> clauses = new ArrayList<>();
        clauses.add(p.baseFilter());
        if (request.isLivePoolOnly()) {
            clauses.add(p.livePoolFilter());
        }
        if (request.isAvailableOnly()) {
            clauses.add(p.availableFilter());
        }
        if (Objects.nonNull(request.getAcquirableValueCap())) {
            String value = request.isIncludeUnvalued() ? "(" + p.value() + " IS NULL OR " + p.value() + " <= :acquirableCap)" : p.value() + " <= :acquirableCap";
            clauses.add("(" + value + " OR COALESCE(" + p.freeAgentSelect() + ", FALSE) OR " + p.contractUntil() + " <= (" + p.gameDate() + " + (CAST(:contractMonths AS INTEGER) * INTERVAL '1 month')))");
            params.addValue("acquirableCap", request.getAcquirableValueCap());
            params.addValue("contractMonths", Objects.isNull(request.getContractMonthsMax()) ? 0 : request.getContractMonthsMax());
        }
        params.addValue("gv", request.getGameVersionId());
        params.addValue("careerId", request.getCareerId(), java.sql.Types.BIGINT);
        if (Objects.nonNull(request.getQuery()) && !request.getQuery().isBlank()) {
            clauses.add("b.full_name ILIKE :q ESCAPE '!'");
            params.addValue("q", "%" + request.getQuery().trim().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
        }
        if (!request.getPositions().isEmpty()) {
            clauses.add(p.positions() + " && string_to_array(:positions, ',')");
            params.addValue("positions", String.join(",", request.getPositions()));
        }
        if (!request.getAccelerate().isEmpty()) {
            clauses.add(p.accelerate() + " IN (:accelerate)");
            params.addValue("accelerate", request.getAccelerate());
        }
        addPlaystyles(p, request, clauses, params);
        addAttrBounds(p, request.getAttrMin(), ">=", "min", clauses, params);
        addAttrBounds(p, request.getAttrMax(), "<=", "max", clauses, params);
        addBound(request.getOverallMin(), p.overall() + " >= :overallMin", "overallMin", clauses, params);
        addBound(request.getOverallMax(), p.overall() + " <= :overallMax", "overallMax", clauses, params);
        addBound(request.getPotentialMin(), p.potential() + " >= :potentialMin", "potentialMin", clauses, params);
        addBound(request.getPotentialMax(), p.potential() + " <= :potentialMax", "potentialMax", clauses, params);
        addBound(request.getValueMax(), request.isIncludeUnvalued()
                ? "(" + p.value() + " IS NULL OR " + p.value() + " <= :valueMax)" : p.value() + " <= :valueMax", "valueMax", clauses, params);
        addBound(request.getClubId(), p.clubId() + " = :clubId", "clubId", clauses, params);
        addBound(request.getTeamId(), p.teamId() + " = :teamId", "teamId", clauses, params);
        if (request.isFreeAgent()) {
            clauses.add(p.freeAgentFilter());
        }
        addBound(request.getLeagueId(), p.leagueId() + " = :leagueId", "leagueId", clauses, params);
        if (Objects.nonNull(request.getLeagueIds()) && !request.getLeagueIds().isEmpty()) {
            clauses.add("(" + p.leagueId() + " IN (:leagueIds) OR COALESCE(" + p.freeAgentSelect() + ", FALSE))");
            params.addValue("leagueIds", request.getLeagueIds());
        }
        addBound(request.getNationalityId(), "b.nationality_id = :nationalityId", "nationalityId", clauses, params);
        addBound(request.getGender(), "b.gender = :gender", "gender", clauses, params);
        addBound(request.getAgeMin(), ageYears(p) + " >= :ageMin", "ageMin", clauses, params);
        addBound(request.getAgeMax(), ageYears(p) + " <= :ageMax", "ageMax", clauses, params);
        addBound(request.getWeakFootMin(), p.weakFoot() + " >= :weakFootMin", "weakFootMin", clauses, params);
        addBound(request.getSkillMovesMin(), p.skillMoves() + " >= :skillMovesMin", "skillMovesMin", clauses, params);
        if (Objects.nonNull(request.getFoot()) && !request.getFoot().isBlank()) {
            clauses.add("b.preferred_foot = :foot");
            params.addValue("foot", request.getFoot());
        }
        addBound(request.getIdFilter(), "b.id = :playerId", "playerId", clauses, params);
        addIds(request.getIds(), "b.id IN (:ids)", "ids", clauses, params);
        if (Objects.isNull(request.getIds()) || request.getIds().isEmpty()) {
            clauses.add("b.retired_at IS NULL");
        }
        addIds(request.getExcludeIds(), "b.id NOT IN (:excludeIds)", "excludeIds", clauses, params);
        return " WHERE " + String.join(" AND ", clauses);
    }

    private static String ageYears(SearchProfile p) {
        return "EXTRACT(YEAR FROM age(" + p.gameDate() + ", b.dob))";
    }

    private void addPlaystyles(SearchProfile p, PlayerSearchRequest request, List<String> clauses, MapSqlParameterSource params) {
        for (int i = 0; i < request.getPlaystyles().size(); i++) {
            clauses.add("jsonb_exists(" + p.playstyles() + ", :ps" + i + ")");
            params.addValue("ps" + i, request.getPlaystyles().get(i));
        }
    }

    private void addAttrBounds(SearchProfile p, Map<String, Integer> bounds, String operator, String prefix, List<String> clauses,
                               MapSqlParameterSource params) {
        int i = 0;
        for (Map.Entry<String, Integer> bound : bounds.entrySet()) {
            Attr attr = Attr.fromKey(bound.getKey()).orElseThrow(() -> new IllegalArgumentException(bound.getKey()));
            String name = prefix + "Attr" + i++;
            clauses.add(p.attr(attr) + " " + operator + " :" + name);
            params.addValue(name, bound.getValue());
        }
    }

    private void addIds(List<Long> ids, String clause, String name, List<String> clauses, MapSqlParameterSource params) {
        if (Objects.nonNull(ids) && !ids.isEmpty()) {
            clauses.add(clause);
            params.addValue(name, ids);
        }
    }

    private void addBound(Number value, String clause, String name, List<String> clauses, MapSqlParameterSource params) {
        if (Objects.nonNull(value)) {
            clauses.add(clause);
            params.addValue(name, value);
        }
    }

    private String orderBy(SearchProfile p, PlayerSearchRequest request, MapSqlParameterSource params) {
        if (PRESCORE_SORT.equals(request.getSort()) && Objects.nonNull(request.getPrescoreWeights()) && !request.getPrescoreWeights().isEmpty()) {
            return prescore(p, request.getPrescoreWeights(), params) + " DESC NULLS LAST, b.id ASC";
        }
        String key = Objects.isNull(request.getSort()) ? "overall" : request.getSort().replace("_", "").toLowerCase();
        boolean ascending = request.isAscending();
        String column = switch (key) {
            case "overall" -> p.overall();
            case "name" -> "b.full_name";
            case "potential" -> p.potential();
            case "value" -> p.value();
            case "club" -> p.clubName();
            case "league" -> p.leagueName();
            case "nationality" -> "n.name";
            case "accelerate" -> p.accelerate();
            case "position" -> p.positions() + "[1]";
            case "weakfoot" -> p.weakFoot();
            case "skillmoves" -> p.skillMoves();
            default -> null;
        };
        if (AGE_SORT.equals(key)) {
            // Genç → yaşlı sıralaması doğum tarihinin ters yönüdür.
            column = "b.dob";
            ascending = !ascending;
        }
        if (Objects.isNull(column)) {
            column = Attr.fromKey(key).map(p::attr).orElse(p.overall());
        }
        return column + (ascending ? " ASC" : " DESC") + " NULLS LAST, b.id ASC";
    }

    /** Σ w_i·attr_i: ağırlıklar bağlı parametre (SQL enjeksiyonu yok); öneri hattının aday havuzunu rolün özelliklerine göre sıralar. */
    private static String prescore(SearchProfile p, Map<Attr, Double> weights, MapSqlParameterSource params) {
        List<String> terms = new ArrayList<>();
        int i = 0;
        for (Map.Entry<Attr, Double> e : weights.entrySet()) {
            String name = "psw" + i++;
            terms.add("COALESCE(" + p.attr(e.getKey()) + ", 0) * :" + name);
            params.addValue(name, e.getValue());
        }
        return "(" + String.join(" + ", terms) + ")";
    }

    private PlayerSummaryResponse toSummary(ResultSet rs) throws SQLException {
        Date dob = rs.getDate("dob");
        Array positions = rs.getArray("positions");
        List<String> positionList = Arrays.asList((String[]) positions.getArray());
        com.fcareer.ut.service.FaceStatsService.FaceStats face = faceStats(rs, positionList);
        return PlayerSummaryResponse.builder()
                .id(rs.getLong("id"))
                .name(rs.getString("name"))
                .age(Objects.isNull(dob) ? null : Period.between(dob.toLocalDate(), rs.getDate("ref_date").toLocalDate()).getYears())
                .overall(rs.getInt("overall"))
                .positions(positionList)
                .faceStats(Objects.isNull(face) ? null : face.values())
                .faceLabels(Objects.isNull(face) ? null : face.labels())
                .faceStatsSource(Objects.isNull(face) ? null : face.source())
                .faceStatsApproximate(Objects.nonNull(face) && com.fcareer.ut.service.FaceStatsService.COMPUTED.equals(face.source()) && !faceStatsService.weightsVerified())
                .clubId(nullableInt(rs, "club_id"))
                .club(rs.getString("club"))
                .league(rs.getString("league"))
                .nationality(rs.getString("nationality"))
                .gender(nullableInt(rs, "gender"))
                .preferredFoot(rs.getString("foot"))
                .weakFoot(nullableInt(rs, "weak_foot"))
                .skillMoves(nullableInt(rs, "skill_moves"))
                .accelerate(rs.getString("accelerate"))
                .runStyle(nullableInt(rs, "run_style"))
                .potential(nullableInt(rs, "potential"))
                .valueEur(nullableLong(rs, "value_eur"))
                .valuationEstimated(rs.getBoolean("estimated"))
                .freeAgent(rs.getBoolean("free_agent"))
                .faceAssetId(nullableLong(rs, "ea_id"))
                .build();
    }

    /** Resmî değer varsa o, yoksa kariyer/katalog attribute'larından hesaplanan (F10). */
    private com.fcareer.ut.service.FaceStatsService.FaceStats faceStats(ResultSet rs, List<String> positions) throws SQLException {
        Array official = rs.getArray("face_official");
        List<Integer> officialValues = Objects.isNull(official) ? null : Arrays.stream((Object[]) official.getArray()).map(v -> ((Number) v).intValue()).toList();
        Map<Attr, Integer> attrs = new java.util.EnumMap<>(Attr.class);
        try {
            com.fasterxml.jackson.databind.JsonNode json = JSON.readTree(rs.getString("attrs_json"));
            for (Attr attr : Attr.values()) {
                if (json.hasNonNull(attr.getColumn())) {
                    attrs.put(attr, json.get(attr.getColumn()).asInt());
                }
            }
        } catch (java.io.IOException e) {
            throw new SQLException("attrs_json unreadable", e);
        }
        return faceStatsService.resolve(officialValues, attrs, !positions.isEmpty() && "GK".equals(positions.get(0)));
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
