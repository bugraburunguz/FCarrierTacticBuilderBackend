package com.fcareer.catalog.squad;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.catalog.entity.PlayerRatingEntity;
import com.fcareer.catalog.entity.PlayerValuationEntity;
import com.fcareer.catalog.model.enums.ValuationSource;
import com.fcareer.catalog.mapper.RatingAttrAccessor;
import com.fcareer.fitengine.model.Attr;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.fcareer.catalog.squad.SquadVersionSqlConstants.ARCHIVE_CLUB_PROFILES;
import static com.fcareer.catalog.squad.SquadVersionSqlConstants.ARCHIVE_RATINGS;
import static com.fcareer.catalog.squad.SquadVersionSqlConstants.ARCHIVE_VALUATIONS;
import static com.fcareer.catalog.squad.SquadVersionSqlConstants.FIND_ARCHIVED_SQUAD_VERSION_OF_CAREER;
import static com.fcareer.catalog.squad.SquadVersionSqlConstants.FIND_VERSIONED_RATINGS;
import static com.fcareer.catalog.squad.SquadVersionSqlConstants.FIND_VERSIONED_VALUATIONS;
import static com.fcareer.catalog.squad.SquadVersionSqlConstants.CREATE;
import static com.fcareer.catalog.squad.SquadVersionSqlConstants.CREATE_FIRST;
import static com.fcareer.catalog.squad.SquadVersionSqlConstants.FIND_ARCHIVED_CATALOG_LOANED_OUT;
import static com.fcareer.catalog.squad.SquadVersionSqlConstants.FIND_ARCHIVED_CATALOG_RATING;
import static com.fcareer.catalog.squad.SquadVersionSqlConstants.FIND_ARCHIVED_CATALOG_SQUAD;
import static com.fcareer.catalog.squad.SquadVersionSqlConstants.FIND_ALL_BY_GAME_VERSION;
import static com.fcareer.catalog.squad.SquadVersionSqlConstants.FIND_BY_ID;
import static com.fcareer.catalog.squad.SquadVersionSqlConstants.FIND_LATEST;
import static com.fcareer.catalog.squad.SquadVersionSqlConstants.IS_LATEST;

@Slf4j
@Repository
@RequiredArgsConstructor
public class SquadVersionRepository {

    private static final String ID = "id";
    private static final String GAME_VERSION_ID = "gameVersionId";
    private static final String SQUAD_VERSION_ID = "squadVersionId";
    private static final String CLUB_ID = "clubId";
    private static final String EA_TEAM_ID = "eaTeamId";
    private static final String BASE_PLAYER_ID = "basePlayerId";
    private static final String CAREER_ID = "careerId";
    private static final String IDS = "ids";

    private static final RowMapper<SquadVersion> ROW_MAPPER = SquadVersionRepository::mapRow;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RatingAttrAccessor ratingAttrAccessor;

    public Optional<SquadVersion> findLatest(short gameVersionId) {
        return jdbc.query(FIND_LATEST, new MapSqlParameterSource(GAME_VERSION_ID, gameVersionId), ROW_MAPPER).stream().findFirst();
    }

    public Optional<SquadVersion> findById(long id) {
        return jdbc.query(FIND_BY_ID, new MapSqlParameterSource(ID, id), ROW_MAPPER).stream().findFirst();
    }

    public List<SquadVersion> findAllByGameVersion(short gameVersionId) {
        return jdbc.query(FIND_ALL_BY_GAME_VERSION, new MapSqlParameterSource(GAME_VERSION_ID, gameVersionId), ROW_MAPPER);
    }

    public boolean isLatest(long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(IS_LATEST, new MapSqlParameterSource(ID, id), Boolean.class));
    }

    public SquadVersion create(short gameVersionId, String label, String gameBuild, Instant exportedAt, int playerCount) {
        MapSqlParameterSource params = new MapSqlParameterSource(GAME_VERSION_ID, gameVersionId)
                .addValue("label", label)
                .addValue("gameBuild", gameBuild)
                .addValue("exportedAt", Objects.isNull(exportedAt) ? null : Timestamp.from(exportedAt))
                .addValue("playerCount", playerCount);
        SquadVersion created = jdbc.queryForObject(CREATE, params, ROW_MAPPER);
        log.info("Squad version created squadVersionId : {} gameVersionId : {} versionNo : {}", created.id(), gameVersionId, created.versionNo());
        return created;
    }

    public SquadArchiveResult archiveCurrent(long squadVersionId, short gameVersionId) {
        MapSqlParameterSource params = new MapSqlParameterSource(SQUAD_VERSION_ID, squadVersionId).addValue(GAME_VERSION_ID, gameVersionId);
        int players = jdbc.update(ARCHIVE_RATINGS, params);
        int clubs = jdbc.update(ARCHIVE_CLUB_PROFILES, params);
        int valuations = jdbc.update(ARCHIVE_VALUATIONS, params);
        log.info("Squad version archived squadVersionId : {} players : {} clubs : {} valuations : {}", squadVersionId, players, clubs, valuations);
        return new SquadArchiveResult(players, clubs, valuations);
    }

    public Optional<Long> findArchivedSquadVersionOfCareer(long careerId) {
        return jdbc.queryForList(FIND_ARCHIVED_SQUAD_VERSION_OF_CAREER, new MapSqlParameterSource(CAREER_ID, careerId), Long.class).stream().findFirst();
    }

    public List<PlayerRatingEntity> findVersionedRatings(Collection<Long> basePlayerIds, long squadVersionId, short gameVersionId, Long careerId) {
        if (basePlayerIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query(FIND_VERSIONED_RATINGS, buildVersionedParams(basePlayerIds, squadVersionId, gameVersionId, careerId), (rs, i) -> toArchivedRating(rs));
    }

    public List<PlayerValuationEntity> findVersionedValuations(Collection<Long> basePlayerIds, long squadVersionId, short gameVersionId, Long careerId) {
        if (basePlayerIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query(FIND_VERSIONED_VALUATIONS, buildVersionedParams(basePlayerIds, squadVersionId, gameVersionId, careerId), (rs, i) -> toValuation(rs));
    }

    private static MapSqlParameterSource buildVersionedParams(Collection<Long> basePlayerIds, long squadVersionId, short gameVersionId, Long careerId) {
        return new MapSqlParameterSource(SQUAD_VERSION_ID, squadVersionId).addValue(GAME_VERSION_ID, gameVersionId).addValue(IDS, basePlayerIds)
                .addValue(CAREER_ID, careerId, Types.BIGINT);
    }

    private static PlayerValuationEntity toValuation(ResultSet rs) throws SQLException {
        PlayerValuationEntity valuation = new PlayerValuationEntity();
        valuation.setBasePlayerId(rs.getLong("base_player_id"));
        valuation.setGameVersionId(rs.getShort("game_version_id"));
        valuation.setSource(ValuationSource.MODELED);
        valuation.setPotential(rs.getObject("potential", Short.class));
        valuation.setValueEur(rs.getObject("value_eur", Long.class));
        valuation.setWageEur(rs.getObject("wage_eur", Long.class));
        valuation.setModelVersion(rs.getString("model_version"));
        return valuation;
    }

    public void createFirstIfMissing(short gameVersionId, String label) {
        int created = jdbc.update(CREATE_FIRST, new MapSqlParameterSource(GAME_VERSION_ID, gameVersionId).addValue("label", label));
        log.info("Squad version first ensured gameVersionId : {} created : {}", gameVersionId, created);
    }

    public List<PlayerRatingEntity> findArchivedCatalogSquad(int clubId, long squadVersionId) {
        return jdbc.query(FIND_ARCHIVED_CATALOG_SQUAD, new MapSqlParameterSource(SQUAD_VERSION_ID, squadVersionId).addValue(CLUB_ID, clubId),
                (rs, i) -> toArchivedRating(rs));
    }

    public List<PlayerRatingEntity> findArchivedCatalogLoanedOut(int eaTeamId, long squadVersionId) {
        return jdbc.query(FIND_ARCHIVED_CATALOG_LOANED_OUT, new MapSqlParameterSource(SQUAD_VERSION_ID, squadVersionId).addValue(EA_TEAM_ID, eaTeamId),
                (rs, i) -> toArchivedRating(rs));
    }

    public Optional<PlayerRatingEntity> findArchivedCatalogRating(long basePlayerId, long squadVersionId) {
        return jdbc.query(FIND_ARCHIVED_CATALOG_RATING, new MapSqlParameterSource(SQUAD_VERSION_ID, squadVersionId).addValue(BASE_PLAYER_ID, basePlayerId),
                (rs, i) -> toArchivedRating(rs)).stream().findFirst();
    }

    private PlayerRatingEntity toArchivedRating(ResultSet rs) throws SQLException {
        PlayerRatingEntity rating = new PlayerRatingEntity();
        rating.setBasePlayerId(rs.getLong("base_player_id"));
        rating.setGameVersionId(rs.getShort("game_version_id"));
        rating.setClubId(rs.getObject("club_id", Integer.class));
        rating.setLoanFromTeamId(rs.getObject("loan_from_team_id", Integer.class));
        rating.setLoanUntil(rs.getObject("loan_until", java.time.LocalDate.class));
        rating.setOverall(rs.getShort("overall"));
        rating.setPositions(Arrays.asList((String[]) rs.getArray("positions").getArray()));
        rating.setAccelerate(rs.getString("accelerate"));
        rating.setWeakFoot(rs.getObject("weak_foot", Short.class));
        rating.setSkillMoves(rs.getObject("skill_moves", Short.class));
        rating.setBodyType(rs.getString("body_type"));
        rating.setPlaystyles(readPlaystyles(rs.getString("playstyles")));
        Map<Attr, Integer> attrs = new EnumMap<>(Attr.class);
        for (Attr attr : Attr.values()) {
            Integer value = rs.getObject(attr.getColumn(), Integer.class);
            if (Objects.nonNull(value)) {
                attrs.put(attr, value);
            }
        }
        ratingAttrAccessor.write(rating, attrs);
        return rating;
    }

    private Map<String, Integer> readPlaystyles(String json) {
        if (Objects.isNull(json)) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Integer>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("player_rating_archive playstyles unreadable", e);
        }
    }

    private static SquadVersion mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new SquadVersion(rs.getLong("id"), rs.getShort("game_version_id"), rs.getInt("version_no"), rs.getString("label"),
                rs.getString("game_build"), toInstant(rs.getTimestamp("exported_at")), rs.getInt("player_count"), toInstant(rs.getTimestamp("created_at")));
    }

    private static Instant toInstant(Timestamp timestamp) {
        return Objects.isNull(timestamp) ? null : timestamp.toInstant();
    }
}
