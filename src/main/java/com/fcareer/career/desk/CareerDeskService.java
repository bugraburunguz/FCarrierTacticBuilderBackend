package com.fcareer.career.desk;

import com.fcareer.career.desk.CareerDeskModels.Backup;
import com.fcareer.career.desk.CareerDeskModels.DiffResult;
import com.fcareer.career.desk.CareerDeskModels.DiffRow;
import com.fcareer.career.desk.CareerDeskModels.HistoryPoint;
import com.fcareer.career.desk.CareerDeskModels.OvrBatch;
import com.fcareer.career.desk.CareerDeskModels.OvrUpdate;
import com.fcareer.career.desk.CareerDeskModels.SeasonRecordDto;
import com.fcareer.career.desk.CareerDeskModels.SeasonRecordRequest;
import com.fcareer.career.desk.CareerDeskModels.ShortlistDto;
import com.fcareer.career.desk.CareerDeskModels.ShortlistRequest;
import com.fcareer.career.desk.CareerDeskModels.ShortlistViewDto;
import com.fcareer.career.desk.CareerDeskModels.SnapshotDto;
import com.fcareer.career.desk.CareerDeskModels.TagDto;
import com.fcareer.career.entity.CareerEntity;
import com.fcareer.career.service.CareerService;
import com.fcareer.catalog.model.response.PlayerSummaryResponse;
import com.fcareer.catalog.service.PlayerService;
import com.fcareer.common.exception.InvalidRosterEventException;
import com.fcareer.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Kariyer masası altyapısı (13): snapshot geçmişi ve fark raporu, oyuncu gelişim eğrisi, elle OVR güncelleme, etiket, kısa liste, sezon kayıtları, yedek. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CareerDeskService {

    private static final int MAX_DIFF_ROWS = 400;

    private final NamedParameterJdbcTemplate jdbc;
    private final CareerService careerService;
    private final SpoilerPolicy spoilerPolicy;
    private final PlayerService playerService;
    private final CareerDeskProperties properties;
    private final com.fcareer.ingestion.base.CareerPlayerWriter careerPlayerWriter;
    private static final String SNAPSHOT_COLUMNS = "id, taken_at, season, transfer_window, source, player_count, note, game_date, budget_eur, squad_wage_eur";
    private static final String SNAPSHOT_ATTRS = java.util.stream.IntStream.range(0, com.fcareer.fitengine.model.Attr.values().length)
            .mapToObj((i) -> "'" + com.fcareer.fitengine.model.Attr.values()[i].getColumn() + "', cp.attrs[" + (i + 1) + "],").collect(java.util.stream.Collectors.joining(" "));

    // ---- snapshot ----

    /** Canlı kariyer verisinin (career_player_data) o anki halini geçmişe yazar; saklama sınırı fcareer.career.snapshot-retention ile ayarlanır (varsayılan sınırsız). */
    @Transactional
    public long takeSnapshot(long careerId, String source, String note) {
        Integer season = jdbc.queryForList("SELECT season FROM career WHERE id = :c", Map.of("c", careerId), Integer.class).stream().findFirst().orElse(null);
        Long id = jdbc.queryForObject("INSERT INTO career_snapshot (career_id, season, source, note, game_date) VALUES (:c, :s, :src, :n, (SELECT current_game_date FROM career WHERE id = :c)) RETURNING id",
                new MapSqlParameterSource().addValue("c", careerId).addValue("s", season).addValue("src", source).addValue("n", note), Long.class);
        careerPlayerWriter.ensureSeeded(careerId);
        int count = jdbc.update("""
                INSERT INTO career_snapshot_player (snapshot_id, base_player_id, overall, potential, team_id, free_agent, positions, attrs, value_eur, wage_eur, age)
                SELECT :id, cp.player_id, cp.ovr, cp.pot, cp.team_id, cp.free_agent, cp.positions,
                       jsonb_build_object(""" + SNAPSHOT_ATTRS + """
                         'weak_foot', cp.weak_foot, 'skill_moves', cp.skill_moves, 'accelerate', cp.accelerate, 'playstyles', cp.playstyles,
                         'run_style1', cp.run_style1, 'run_style2', cp.run_style2, 'body_type', cp.body_type),
                       v.value_eur, v.wage_eur,
                       CASE WHEN b.dob IS NULL THEN NULL ELSE date_part('year', age(COALESCE((SELECT game_date FROM career_snapshot WHERE id = :id), CURRENT_DATE), b.dob))::smallint END
                FROM career_player cp
                JOIN career k ON k.id = cp.career_id
                JOIN base_player b ON b.id = cp.player_id
                LEFT JOIN player_valuation v ON v.base_player_id = cp.player_id AND v.game_version_id = k.game_version_id AND v.source = 'IMPORT' AND v.owner_user_id = k.user_id
                WHERE cp.career_id = :c AND cp.live""", Map.of("id", id, "c", careerId));
        // Finans Δ (15 §7.2): kadro maaş toplamı yalnız her kadro oyuncusunun maaşı biliniyorsa yazılır (careerFacets.squad_wage ile aynı kural).
        jdbc.update("""
                UPDATE career_snapshot SET player_count = :n,
                       budget_eur = (SELECT budget_eur FROM career WHERE id = :c),
                       squad_wage_eur = (SELECT CASE WHEN count(*) > 0 AND count(cp.wage_eur) = count(*) THEN sum(cp.wage_eur) END
                                           FROM user_player_state s LEFT JOIN career_player cp ON cp.career_id = s.career_id AND cp.player_id = s.base_player_id
                                          WHERE s.career_id = :c)
                 WHERE id = :id""", Map.of("n", count, "id", id, "c", careerId));
        Integer keep = properties.getSnapshotRetention();
        if (Objects.nonNull(keep) && keep > 0) {
            jdbc.update("DELETE FROM career_snapshot WHERE career_id = :c AND id NOT IN (SELECT id FROM career_snapshot WHERE career_id = :c ORDER BY id DESC LIMIT :k)",
                    Map.of("c", careerId, "k", keep));
        }
        log.info("Career snapshot taken careerId : {} snapshotId : {} source : {} players : {}", careerId, id, source, count);
        return id;
    }

    @Transactional(readOnly = true)
    public List<SnapshotDto> snapshots(Long userId, Long careerId) {
        careerService.requireOwned(userId, careerId);
        return jdbc.query("SELECT " + SNAPSHOT_COLUMNS + " FROM career_snapshot WHERE career_id = :c ORDER BY id DESC",
                Map.of("c", careerId), (rs, i) -> snapshotOf(rs));
    }

    /** from/to verilmezse son iki snapshot karşılaştırılır. Yalnız değişenler döner: overall/potansiyel, takım değişimi, yeni ve kaybolan oyuncular. */
    @Transactional(readOnly = true)
    public DiffResult diff(Long userId, Long careerId, Long from, Long to) {
        careerService.requireOwned(userId, careerId);
        List<Long> ids = jdbc.queryForList("SELECT id FROM career_snapshot WHERE career_id = :c ORDER BY id DESC LIMIT 2", Map.of("c", careerId), Long.class);
        Long toId = Objects.nonNull(to) ? to : (ids.isEmpty() ? null : ids.get(0));
        Long fromId = Objects.nonNull(from) ? from : (ids.size() < 2 ? null : ids.get(1));
        if (Objects.isNull(toId) || Objects.isNull(fromId)) {
            return new DiffResult(fromId, toId, 0, 0, 0, 0, List.of());
        }
        requireSnapshotOf(careerId, fromId);
        requireSnapshotOf(careerId, toId);
        List<DiffRow> rows = jdbc.query("""
                SELECT COALESCE(b.base_player_id, a.base_player_id) AS pid, p.full_name,
                       a.overall AS a_ovr, a.potential AS a_pot, a.team_id AS a_team, b.overall AS b_ovr, b.potential AS b_pot, b.team_id AS b_team,
                       (a.base_player_id IS NULL) AS added, (b.base_player_id IS NULL) AS removed
                FROM (SELECT * FROM career_snapshot_player WHERE snapshot_id = :from) a
                FULL OUTER JOIN (SELECT * FROM career_snapshot_player WHERE snapshot_id = :to) b ON a.base_player_id = b.base_player_id
                JOIN base_player p ON p.id = COALESCE(b.base_player_id, a.base_player_id)
                WHERE a.base_player_id IS NULL OR b.base_player_id IS NULL OR a.overall <> b.overall OR a.potential <> b.potential OR a.team_id IS DISTINCT FROM b.team_id
                ORDER BY ABS(COALESCE(b.overall, 0) - COALESCE(a.overall, 0)) DESC, pid LIMIT :limit
                """, new MapSqlParameterSource().addValue("from", fromId).addValue("to", toId).addValue("limit", MAX_DIFF_ROWS), (rs, i) -> {
            boolean added = rs.getBoolean("added");
            boolean removed = rs.getBoolean("removed");
            Integer teamFrom = (Integer) rs.getObject("a_team");
            Integer teamTo = (Integer) rs.getObject("b_team");
            String kind = added ? "NEW" : removed ? "REMOVED" : !Objects.equals(teamFrom, teamTo) ? "TRANSFER" : "CHANGED";
            return new DiffRow(rs.getLong("pid"), rs.getString("full_name"), kind, (Integer) rs.getObject("a_ovr"), (Integer) rs.getObject("b_ovr"),
                    (Integer) rs.getObject("a_pot"), (Integer) rs.getObject("b_pot"), teamFrom, teamTo);
        });
        List<DiffRow> shown = rows.stream().map(r -> new DiffRow(r.playerId(), r.name(), r.kind(), r.overallFrom(), r.overallTo(), spoilerPolicy.bandOrSame(careerId, r.potentialFrom()),
                spoilerPolicy.bandOrSame(careerId, r.potentialTo()), r.teamFrom(), r.teamTo())).toList();
        return new DiffResult(fromId, toId, count(rows, "CHANGED"), count(rows, "TRANSFER"), count(rows, "NEW"), count(rows, "REMOVED"), shown);
    }

    private static int count(List<DiffRow> rows, String kind) {
        return (int) rows.stream().filter(r -> r.kind().equals(kind)).count();
    }

    private void requireSnapshotOf(long careerId, long snapshotId) {
        Integer found = jdbc.queryForObject("SELECT count(*) FROM career_snapshot WHERE id = :s AND career_id = :c", Map.of("s", snapshotId, "c", careerId), Integer.class);
        if (found == null || found == 0) {
            throw new NotFoundException("snapshot", snapshotId);
        }
    }

    /** Oyuncunun snapshot'lar boyunca OVR/POT çizgisi (gelişim eğrisi, plato tespiti). */
    @Transactional(readOnly = true)
    public List<HistoryPoint> history(Long userId, Long careerId, Long playerId) {
        careerService.requireOwned(userId, careerId);
        return jdbc.query("""
                SELECT s.id, s.taken_at, s.season, s.game_date, p.overall, p.potential, p.team_id FROM career_snapshot s
                JOIN career_snapshot_player p ON p.snapshot_id = s.id AND p.base_player_id = :p
                WHERE s.career_id = :c ORDER BY s.id
                """, Map.of("c", careerId, "p", playerId), (rs, i) -> new HistoryPoint(rs.getLong("id"), rs.getTimestamp("taken_at").toInstant(), (Integer) rs.getObject("season"),
                rs.getInt("overall"), spoilerPolicy.bandOrSame(careerId, rs.getInt("potential")), (Integer) rs.getObject("team_id"), gameDateOf(rs)));
    }

    /** Konsol oyuncusu için sezon arası hızlı güncelleme: yalnız değişen OVR/POT girilir, canlı veri güncellenir ve MANUAL snapshot alınır. */
    @Transactional
    public SnapshotDto updateRatings(Long userId, Long careerId, OvrBatch batch) {
        careerService.requireOwned(userId, careerId);
        List<Long> squad = jdbc.queryForList("SELECT base_player_id FROM user_player_state WHERE career_id = :c", Map.of("c", careerId), Long.class);
        for (OvrUpdate update : batch.updates()) {
            if (!squad.contains(update.playerId())) {
                throw new InvalidRosterEventException("player not in squad: " + update.playerId());
            }
            int potential = Objects.nonNull(update.potential()) ? update.potential() : Math.max(update.overall(), currentPotential(careerId, update.playerId(), update.overall()));
            jdbc.update("""
                    INSERT INTO career_player_data (career_id, base_player_id, overall, potential, team_id, free_agent) VALUES (:c, :p, :o, :pot, NULL, false)
                    ON CONFLICT (career_id, base_player_id) DO UPDATE SET overall = EXCLUDED.overall, potential = EXCLUDED.potential
                    """, new MapSqlParameterSource().addValue("c", careerId).addValue("p", update.playerId()).addValue("o", update.overall()).addValue("pot", potential));
            jdbc.update("UPDATE user_player_state SET dynamic_potential = :pot, updated_at = now() WHERE career_id = :c AND base_player_id = :p",
                    new MapSqlParameterSource().addValue("c", careerId).addValue("p", update.playerId()).addValue("pot", potential));
        }
        careerPlayerWriter.ensureSeeded(careerId);
        careerPlayerWriter.syncPlayers(careerId, batch.updates().stream().map(OvrUpdate::playerId).toList());
        long id = takeSnapshot(careerId, "MANUAL", batch.note());
        return jdbc.queryForObject("SELECT " + SNAPSHOT_COLUMNS + " FROM career_snapshot WHERE id = :id", Map.of("id", id),
                (rs, i) -> snapshotOf(rs));
    }

    private static SnapshotDto snapshotOf(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new SnapshotDto(rs.getLong("id"), rs.getTimestamp("taken_at").toInstant(), (Integer) rs.getObject("season"), rs.getString("transfer_window"),
                rs.getString("source"), rs.getInt("player_count"), rs.getString("note"), gameDateOf(rs),
                (Long) rs.getObject("budget_eur"), (Long) rs.getObject("squad_wage_eur"));
    }

    private static java.time.LocalDate gameDateOf(java.sql.ResultSet rs) throws java.sql.SQLException {
        java.sql.Date date = rs.getDate("game_date");
        return Objects.isNull(date) ? null : date.toLocalDate();
    }

    private int currentPotential(long careerId, long playerId, int overall) {
        return jdbc.queryForList("SELECT potential FROM career_player_data WHERE career_id = :c AND base_player_id = :p", Map.of("c", careerId, "p", playerId), Integer.class)
                .stream().findFirst().orElse(overall);
    }

    // ---- etiket / kısa liste / sezon ----

    @Transactional(readOnly = true)
    public List<TagDto> tags(Long userId, Long careerId) {
        careerService.requireOwned(userId, careerId);
        return jdbc.query("SELECT base_player_id, tag FROM career_player_tag WHERE career_id = :c ORDER BY base_player_id", Map.of("c", careerId),
                (rs, i) -> new TagDto(rs.getLong("base_player_id"), rs.getString("tag")));
    }

    @Transactional
    public TagDto setTag(Long userId, Long careerId, Long playerId, String tag) {
        careerService.requireOwned(userId, careerId);
        jdbc.update("INSERT INTO career_player_tag (career_id, base_player_id, tag) VALUES (:c, :p, :t) ON CONFLICT (career_id, base_player_id) DO UPDATE SET tag = EXCLUDED.tag",
                new MapSqlParameterSource().addValue("c", careerId).addValue("p", playerId).addValue("t", tag));
        return new TagDto(playerId, tag);
    }

    @Transactional
    public void clearTag(Long userId, Long careerId, Long playerId) {
        careerService.requireOwned(userId, careerId);
        jdbc.update("DELETE FROM career_player_tag WHERE career_id = :c AND base_player_id = :p", Map.of("c", careerId, "p", playerId));
    }

    @Transactional(readOnly = true)
    public List<ShortlistDto> shortlist(Long userId, Long careerId) {
        careerService.requireOwned(userId, careerId);
        return jdbc.query("SELECT base_player_id, note, max_fee_eur, created_at FROM career_shortlist WHERE career_id = :c ORDER BY created_at DESC", Map.of("c", careerId),
                (rs, i) -> new ShortlistDto(rs.getLong("base_player_id"), rs.getString("note"), (Long) rs.getObject("max_fee_eur"), rs.getTimestamp("created_at").toInstant()));
    }

    /** Kısa liste oyuncu özetiyle (WEB-BUG-13); spoiler modu özet üzerinde uygulanır, katalogdan kalkmış oyuncu özetsiz döner. */
    @Transactional(readOnly = true)
    public List<ShortlistViewDto> shortlistView(Long userId, Long careerId) {
        CareerEntity career = careerService.requireOwned(userId, careerId);
        List<ShortlistDto> rows = shortlist(userId, careerId);
        Map<Long, PlayerSummaryResponse> summaries = playerService.summaries(rows.stream().map(ShortlistDto::playerId).toList(), career.getGameVersionId(), careerId);
        spoilerPolicy.maskAll(careerId, summaries.values());
        return rows.stream().map(r -> new ShortlistViewDto(r.playerId(), r.note(), r.maxFeeEur(), r.createdAt(), summaries.get(r.playerId()))).toList();
    }

    @Transactional
    public void shortlistAdd(Long userId, Long careerId, Long playerId, ShortlistRequest request) {
        careerService.requireOwned(userId, careerId);
        Integer exists = jdbc.queryForObject("SELECT count(*) FROM base_player WHERE id = :p AND (owner_career_id IS NULL OR owner_career_id = :c)", Map.of("p", playerId, "c", careerId), Integer.class);
        if (exists == null || exists == 0) {
            throw new NotFoundException("player", playerId);
        }
        jdbc.update("""
                INSERT INTO career_shortlist (career_id, base_player_id, note, max_fee_eur) VALUES (:c, :p, :n, :f)
                ON CONFLICT (career_id, base_player_id) DO UPDATE SET note = EXCLUDED.note, max_fee_eur = EXCLUDED.max_fee_eur
                """, new MapSqlParameterSource().addValue("c", careerId).addValue("p", playerId).addValue("n", request.note()).addValue("f", request.maxFeeEur()));
    }

    @Transactional
    public void shortlistRemove(Long userId, Long careerId, Long playerId) {
        careerService.requireOwned(userId, careerId);
        jdbc.update("DELETE FROM career_shortlist WHERE career_id = :c AND base_player_id = :p", Map.of("c", careerId, "p", playerId));
    }

    @Transactional(readOnly = true)
    public List<SeasonRecordDto> seasons(Long userId, Long careerId) {
        careerService.requireOwned(userId, careerId);
        return jdbc.query("SELECT season, league_position, trophies, top_scorer_id, note FROM career_season_record WHERE career_id = :c ORDER BY season DESC", Map.of("c", careerId), (rs, i) -> {
            java.sql.Array trophies = rs.getArray("trophies");
            return new SeasonRecordDto(rs.getInt("season"), (Integer) rs.getObject("league_position"), trophies == null ? List.of() : List.of((String[]) trophies.getArray()),
                    (Long) rs.getObject("top_scorer_id"), rs.getString("note"));
        });
    }

    @Transactional
    public void putSeason(Long userId, Long careerId, int season, SeasonRecordRequest request) {
        careerService.requireOwned(userId, careerId);
        String[] trophies = Objects.isNull(request.trophies()) ? new String[0] : request.trophies().toArray(new String[0]);
        jdbc.update("""
                INSERT INTO career_season_record (career_id, season, league_position, trophies, top_scorer_id, note) VALUES (:c, :s, :pos, :t, :top, :n)
                ON CONFLICT (career_id, season) DO UPDATE SET league_position = EXCLUDED.league_position, trophies = EXCLUDED.trophies, top_scorer_id = EXCLUDED.top_scorer_id, note = EXCLUDED.note
                """, new MapSqlParameterSource().addValue("c", careerId).addValue("s", season).addValue("pos", request.leaguePosition())
                .addValue("t", jdbc.getJdbcTemplate().execute((java.sql.Connection con) -> con.createArrayOf("text", trophies))).addValue("top", request.topScorerId()).addValue("n", request.note()));
    }

    // ---- yedek ----

    @Transactional(readOnly = true)
    public Backup backup(Long userId, Long careerId) {
        CareerEntity career = careerService.requireOwned(userId, careerId);
        return new Backup(1, career.getName(), career.getSeason(), career.getBudgetEur(), career.getSpoilerMode(), career.getWageBudgetEur(), career.getChallengeId(),
                tags(userId, careerId), shortlist(userId, careerId), seasons(userId, careerId));
    }

    @Transactional
    public Backup restore(Long userId, Long careerId, Backup backup) {
        careerService.requireOwned(userId, careerId);
        if (backup.version() != 1) {
            throw new InvalidRosterEventException("unsupported backup version");
        }
        jdbc.update("UPDATE career SET spoiler_mode = COALESCE(:s, spoiler_mode), wage_budget_eur = COALESCE(:w, wage_budget_eur), challenge_id = COALESCE(:ch, challenge_id) WHERE id = :c",
                new MapSqlParameterSource().addValue("c", careerId).addValue("s", backup.spoilerMode()).addValue("w", backup.wageBudgetEur()).addValue("ch", backup.challengeId()));
        List<TagDto> tags = Objects.isNull(backup.tags()) ? new ArrayList<>() : backup.tags();
        for (TagDto tag : tags) {
            setTag(userId, careerId, tag.playerId(), tag.tag());
        }
        for (ShortlistDto item : Objects.isNull(backup.shortlist()) ? List.<ShortlistDto>of() : backup.shortlist()) {
            shortlistAdd(userId, careerId, item.playerId(), new ShortlistRequest(item.note(), item.maxFeeEur()));
        }
        for (SeasonRecordDto record : Objects.isNull(backup.seasons()) ? List.<SeasonRecordDto>of() : backup.seasons()) {
            putSeason(userId, careerId, record.season(), new SeasonRecordRequest(record.leaguePosition(), record.trophies(), record.topScorerId(), record.note()));
        }
        return backup(userId, careerId);
    }
}
