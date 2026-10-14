package com.fcareer.versioning.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.versioning.model.ChangeType;
import com.fcareer.versioning.model.PlayerChange;
import com.fcareer.versioning.model.VersionModels.AttrDiff;
import com.fcareer.versioning.model.VersionModels.ChangeSummary;
import com.fcareer.versioning.model.VersionModels.ChangedPlayer;
import com.fcareer.versioning.model.VersionModels.Delta;
import com.fcareer.versioning.model.VersionModels.LatestChanges;
import com.fcareer.versioning.model.VersionModels.PlayerDiff;
import com.fcareer.versioning.model.VersionModels.TextChange;
import com.fcareer.versioning.model.VersionModels.VersionInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class VersioningService {

    public static final String SCOPE_CATALOG = "CATALOG";
    private static final int ID_BATCH = 1000;
    private static final int DEFAULT_CHANGES_LIMIT = 50;
    private static final int MAX_CHANGES_LIMIT = 200;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    /** Bir import'u yeni sürüm olarak kaydeder; yalnızca değişen/yeni/silinen oyuncular saklanır. Değişiklik yoksa sürüm açılmaz. */
    public Optional<Long> recordCatalogVersion(short gameVersionId, String source, String label, boolean full, List<PlayerChange> changes) {
        if (changes.isEmpty()) {
            return Optional.empty();
        }
        Integer next = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version_no), 0) + 1 FROM dataset_version WHERE scope = :s AND career_id IS NULL AND game_version_id = :gv",
                Map.of("s", SCOPE_CATALOG, "gv", gameVersionId), Integer.class);
        Long versionId = jdbc.queryForObject("""
                INSERT INTO dataset_version (scope, game_version_id, version_no, source, label, change_count, is_full)
                VALUES (:s, :gv, :no, :src, :label, :count, :full) RETURNING id""",
                new MapSqlParameterSource().addValue("s", SCOPE_CATALOG).addValue("gv", gameVersionId).addValue("no", next)
                        .addValue("src", source).addValue("label", label).addValue("count", changes.size()).addValue("full", full),
                Long.class);
        List<SqlParameterSource> rows = new ArrayList<>();
        for (PlayerChange c : changes) {
            rows.add(row(versionId, c));
        }
        for (int from = 0; from < rows.size(); from += ID_BATCH) {
            jdbc.batchUpdate("""
                    INSERT INTO player_version_diff (version_id, base_player_id, change_type, ovr_old, ovr_new, pot_old, pot_new, accel_old, accel_new,
                                                     positions_old, positions_new, club_old, club_new, attr_diff, ps_diff)
                    VALUES (:version, :player, :type, :ovrOld, :ovrNew, :potOld, :potNew, :accelOld, :accelNew,
                            :posOld, :posNew, :clubOld, :clubNew, CAST(:attrs AS jsonb), CAST(:styles AS jsonb))""",
                    rows.subList(from, Math.min(from + ID_BATCH, rows.size())).toArray(new SqlParameterSource[0]));
        }
        log.info("Dataset version recorded scope : CATALOG gameVersion : {} versionNo : {} changes : {} full : {}", gameVersionId, next, changes.size(), full);
        return Optional.of(versionId);
    }

    private SqlParameterSource row(Long versionId, PlayerChange c) {
        return new MapSqlParameterSource().addValue("version", versionId).addValue("player", c.basePlayerId()).addValue("type", c.type().name())
                .addValue("ovrOld", c.before() == null ? null : c.before().overall(), java.sql.Types.SMALLINT)
                .addValue("ovrNew", c.after() == null ? null : c.after().overall(), java.sql.Types.SMALLINT)
                .addValue("potOld", c.before() == null ? null : c.before().potential(), java.sql.Types.SMALLINT)
                .addValue("potNew", c.after() == null ? null : c.after().potential(), java.sql.Types.SMALLINT)
                .addValue("accelOld", c.before() == null ? null : c.before().accelerate())
                .addValue("accelNew", c.after() == null ? null : c.after().accelerate())
                .addValue("posOld", c.before() == null ? null : c.before().positions())
                .addValue("posNew", c.after() == null ? null : c.after().positions())
                .addValue("clubOld", c.before() == null ? null : c.before().clubId(), java.sql.Types.INTEGER)
                .addValue("clubNew", c.after() == null ? null : c.after().clubId(), java.sql.Types.INTEGER)
                .addValue("attrs", json(c.attributes().stream().map(a -> Map.of("key", a.key(), "old", a.oldValue(), "new", a.newValue(), "delta", a.delta())).toList()))
                .addValue("styles", Objects.isNull(c.playstyles()) || c.playstyles().isEmpty() ? null : json(Map.of(
                        "added", c.playstyles().added(), "removed", c.playstyles().removed(),
                        "upgraded", c.playstyles().upgraded(), "downgraded", c.playstyles().downgraded())));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("diff json", e);
        }
    }

    private Optional<VersionRef> latest(short gameVersionId) {
        List<VersionRef> found = jdbc.query("""
                SELECT id, version_no FROM dataset_version WHERE scope = :s AND career_id IS NULL AND game_version_id = :gv
                ORDER BY version_no DESC LIMIT 1""", Map.of("s", SCOPE_CATALOG, "gv", gameVersionId),
                (rs, i) -> new VersionRef(rs.getLong("id"), rs.getInt("version_no")));
        return found.stream().findFirst();
    }

    private record VersionRef(long id, int versionNo) {
    }

    /** Liste için: son sürümde bu oyunculardan hangileri değişmiş. Runtime'da diff hesaplanmaz, yalnızca okunur. */
    public Map<Long, ChangeSummary> summaries(Collection<Long> playerIds, short gameVersionId) {
        Map<Long, ChangeSummary> result = new HashMap<>();
        Optional<VersionRef> version = latest(gameVersionId);
        if (version.isEmpty() || playerIds.isEmpty()) {
            return result;
        }
        List<Long> ids = new ArrayList<>(playerIds);
        for (int from = 0; from < ids.size(); from += ID_BATCH) {
            jdbc.query("""
                    SELECT base_player_id, change_type, ovr_old, ovr_new, attr_diff, ps_diff
                    FROM player_version_diff WHERE version_id = :v AND base_player_id IN (:ids)""",
                    new MapSqlParameterSource().addValue("v", version.get().id()).addValue("ids", ids.subList(from, Math.min(from + ID_BATCH, ids.size()))),
                    (ResultSet rs) -> {
                        Integer oldOvr = nullableInt(rs, "ovr_old");
                        Integer newOvr = nullableInt(rs, "ovr_new");
                        int count = size(rs.getString("attr_diff")) + styleCount(rs.getString("ps_diff"));
                        result.put(rs.getLong("base_player_id"), new ChangeSummary(ChangeType.valueOf(rs.getString("change_type")),
                                Objects.isNull(oldOvr) || Objects.isNull(newOvr) ? null : newOvr - oldOvr, count, version.get().versionNo()));
                    });
        }
        return result;
    }

    public Optional<PlayerDiff> diffFor(Long playerId, short gameVersionId) {
        Optional<VersionRef> version = latest(gameVersionId);
        if (version.isEmpty()) {
            return Optional.empty();
        }
        List<PlayerDiff> found = jdbc.query("""
                SELECT d.*, v.imported_at, v.version_no, co.name AS club_old_name, cn.name AS club_new_name
                FROM player_version_diff d JOIN dataset_version v ON v.id = d.version_id
                LEFT JOIN club co ON co.id = d.club_old LEFT JOIN club cn ON cn.id = d.club_new
                WHERE d.version_id = :v AND d.base_player_id = :p""", Map.of("v", version.get().id(), "p", playerId),
                (rs, i) -> toDiff(rs, version.get().versionNo()));
        return found.stream().findFirst();
    }

    private PlayerDiff toDiff(ResultSet rs, int versionNo) throws SQLException {
        Integer ovrOld = nullableInt(rs, "ovr_old");
        Integer ovrNew = nullableInt(rs, "ovr_new");
        Integer potOld = nullableInt(rs, "pot_old");
        Integer potNew = nullableInt(rs, "pot_new");
        String posOld = rs.getString("positions_old");
        String posNew = rs.getString("positions_new");
        String accelOld = rs.getString("accel_old");
        String accelNew = rs.getString("accel_new");
        String clubOld = rs.getString("club_old_name");
        String clubNew = rs.getString("club_new_name");
        List<AttrDiff> attrs = new ArrayList<>();
        for (Map<String, Object> a : readList(rs.getString("attr_diff"))) {
            attrs.add(new AttrDiff((String) a.get("key"), ((Number) a.get("old")).intValue(), ((Number) a.get("new")).intValue(), ((Number) a.get("delta")).intValue()));
        }
        return new PlayerDiff(versionNo, versionNo > 1 ? versionNo - 1 : null, rs.getObject("imported_at", OffsetDateTime.class),
                ChangeType.valueOf(rs.getString("change_type")), delta(ovrOld, ovrNew), delta(potOld, potNew),
                Objects.equals(posOld, posNew) ? null : new TextChange(posOld, posNew),
                Objects.equals(accelOld, accelNew) ? null : new TextChange(accelOld, accelNew),
                Objects.equals(clubOld, clubNew) ? null : new TextChange(clubOld, clubNew),
                attrs, readMap(rs.getString("ps_diff")));
    }

    private static Delta delta(Integer oldValue, Integer newValue) {
        if (Objects.isNull(oldValue) || Objects.isNull(newValue) || oldValue.equals(newValue)) {
            return null;
        }
        return new Delta(oldValue, newValue, newValue - oldValue);
    }

    public List<VersionInfo> versions(short gameVersionId) {
        return jdbc.query("""
                SELECT id, scope, version_no, source, label, imported_at, change_count, is_full FROM dataset_version
                WHERE scope = :s AND career_id IS NULL AND game_version_id = :gv ORDER BY version_no DESC""",
                Map.of("s", SCOPE_CATALOG, "gv", gameVersionId), (rs, i) -> info(rs));
    }

    private VersionInfo info(ResultSet rs) throws SQLException {
        return new VersionInfo(rs.getLong("id"), rs.getString("scope"), rs.getInt("version_no"), rs.getString("source"), rs.getString("label"),
                rs.getObject("imported_at", OffsetDateTime.class), rs.getInt("change_count"), rs.getBoolean("is_full"));
    }

    public Optional<LatestChanges> latestChanges(short gameVersionId, ChangeType type, int limit) {
        List<VersionInfo> infos = jdbc.query("""
                SELECT id, scope, version_no, source, label, imported_at, change_count, is_full FROM dataset_version
                WHERE scope = :s AND career_id IS NULL AND game_version_id = :gv ORDER BY version_no DESC LIMIT 1""",
                Map.of("s", SCOPE_CATALOG, "gv", gameVersionId), (rs, i) -> info(rs));
        if (infos.isEmpty()) {
            return Optional.empty();
        }
        int capped = limit <= 0 ? DEFAULT_CHANGES_LIMIT : Math.min(limit, MAX_CHANGES_LIMIT);
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("v", infos.get(0).id()).addValue("limit", capped);
        String filter = Objects.isNull(type) ? "" : " AND d.change_type = :type";
        if (Objects.nonNull(type)) {
            params.addValue("type", type.name());
        }
        Map<String, List<ChangedPlayer>> groups = new LinkedHashMap<>();
        jdbc.query("SELECT d.base_player_id, d.change_type, d.ovr_old, d.ovr_new, d.attr_diff, b.full_name, co.name AS club_old_name, cn.name AS club_new_name "
                        + "FROM player_version_diff d JOIN base_player b ON b.id = d.base_player_id "
                        + "LEFT JOIN club co ON co.id = d.club_old LEFT JOIN club cn ON cn.id = d.club_new "
                        + "WHERE d.version_id = :v" + filter + " ORDER BY ABS(COALESCE(d.ovr_new, 0) - COALESCE(d.ovr_old, 0)) DESC, b.full_name LIMIT :limit",
                params, (ResultSet rs) -> {
                    groups.computeIfAbsent(rs.getString("change_type"), k -> new ArrayList<>()).add(new ChangedPlayer(
                            rs.getLong("base_player_id"), rs.getString("full_name"), ChangeType.valueOf(rs.getString("change_type")),
                            nullableInt(rs, "ovr_old"), nullableInt(rs, "ovr_new"), rs.getString("club_old_name"), rs.getString("club_new_name"),
                            size(rs.getString("attr_diff"))));
                });
        return Optional.of(new LatestChanges(infos.get(0), groups));
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private int size(String json) {
        return readList(json).size();
    }

    private int styleCount(String json) {
        Map<String, Object> map = readMap(json);
        if (Objects.isNull(map)) {
            return 0;
        }
        return map.values().stream().filter(List.class::isInstance).mapToInt(v -> ((List<?>) v).size()).sum();
    }

    private List<Map<String, Object>> readList(String json) {
        if (Objects.isNull(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private Map<String, Object> readMap(String json) {
        if (Objects.isNull(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
