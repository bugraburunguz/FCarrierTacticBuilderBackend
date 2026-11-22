package com.fcareer.catalog.squad;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.fcareer.catalog.squad.SquadVersionSqlConstants.ARCHIVE_CLUB_PROFILES;
import static com.fcareer.catalog.squad.SquadVersionSqlConstants.ARCHIVE_RATINGS;
import static com.fcareer.catalog.squad.SquadVersionSqlConstants.CREATE;
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

    private static final RowMapper<SquadVersion> ROW_MAPPER = SquadVersionRepository::mapRow;

    private final NamedParameterJdbcTemplate jdbc;

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
        log.info("Squad version archived squadVersionId : {} players : {} clubs : {}", squadVersionId, players, clubs);
        return new SquadArchiveResult(players, clubs);
    }

    private static SquadVersion mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new SquadVersion(rs.getLong("id"), rs.getShort("game_version_id"), rs.getInt("version_no"), rs.getString("label"),
                rs.getString("game_build"), toInstant(rs.getTimestamp("exported_at")), rs.getInt("player_count"), toInstant(rs.getTimestamp("created_at")));
    }

    private static Instant toInstant(Timestamp timestamp) {
        return Objects.isNull(timestamp) ? null : timestamp.toInstant();
    }
}
