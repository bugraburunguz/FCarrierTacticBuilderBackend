package com.fcareer.ut.kb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.ut.kb.KbModels.EvolutionDto;
import com.fcareer.ut.kb.KbModels.ItemDto;
import com.fcareer.ut.kb.KbModels.ItemSearch;
import com.fcareer.ut.kb.KbModels.ObjectiveGroupDto;
import com.fcareer.ut.kb.KbModels.PricePoint;
import com.fcareer.ut.kb.KbModels.PromoDto;
import com.fcareer.ut.kb.KbModels.QueueItem;
import com.fcareer.ut.kb.KbModels.SbcChallengeDto;
import com.fcareer.ut.kb.KbModels.SbcSetDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class KnowledgeBaseRepository {

    private static final String JSONB = "CAST(:%s AS jsonb)";

    private final com.fcareer.ut.service.UtRules rules;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    /** Set gövdesi gelmeden challenge'dan açılan taslak satırın içerik özeti (ContributionService de bunu tanır). */
    public static final String STUB_HASH = "stub";

    // ---- yazma ----

    /**
     * İlk eklemede first_seen_at = now(). İçerik özeti değişince content_changed_at = now(); taslak satırın (ensureSetStub, özet 'stub')
     * ilk gerçek içeriği değişiklik sayılmaz. first_seen_at güncellemede korunur.
     */
    public void upsertSbcSet(long id, short gameVersion, String name, String description, String category, Integer repeatable, Instant endsAt, JsonNode reward, String status, String hash) {
        jdbc.update("""
                INSERT INTO sbc_set (id, game_version_id, name, description, category, repeatable, ends_at, reward, status, content_hash, updated_at, first_seen_at)
                VALUES (:id, :gv, :name, :description, :category, :repeatable, :endsAt, CAST(:reward AS jsonb), :status, :hash, now(), now())
                ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, description = EXCLUDED.description, category = EXCLUDED.category,
                  repeatable = EXCLUDED.repeatable, ends_at = COALESCE(EXCLUDED.ends_at, sbc_set.ends_at), reward = EXCLUDED.reward,
                  status = EXCLUDED.status, content_hash = EXCLUDED.content_hash, updated_at = now(),
                  content_changed_at = CASE WHEN sbc_set.content_hash <> EXCLUDED.content_hash AND sbc_set.content_hash <> :stubHash
                    THEN now() ELSE sbc_set.content_changed_at END
                """, new MapSqlParameterSource().addValue("id", id).addValue("gv", gameVersion).addValue("name", name).addValue("description", description)
                .addValue("category", category).addValue("repeatable", repeatable).addValue("endsAt", ts(endsAt)).addValue("reward", json(reward))
                .addValue("status", status).addValue("hash", hash).addValue("stubHash", STUB_HASH));
    }

    public void ensureSetStub(long id, short gameVersion, String name) {
        jdbc.update("INSERT INTO sbc_set (id, game_version_id, name, status, content_hash) VALUES (:id, :gv, :name, 'PENDING', :stubHash) ON CONFLICT (id) DO NOTHING",
                new MapSqlParameterSource().addValue("id", id).addValue("gv", gameVersion).addValue("name", Objects.requireNonNullElse(name, "SBC #" + id)).addValue("stubHash", STUB_HASH));
    }

    /** formation: bizim formasyon kimliği (eşlenmişse); eaFormation: EA'nın ham kodu (eşlenmese de saklanır); eaType: EA görev tipi (V3-05d-b). */
    public void upsertSbcChallenge(long id, long setId, String name, String formation, String eaFormation, String eaType, String kind, JsonNode requirements,
                                   Integer itemScoreTarget, JsonNode reward, String hash) {
        jdbc.update("""
                INSERT INTO sbc_challenge (id, set_id, name, formation, ea_formation, ea_type, kind, requirements, item_score_target, reward, content_hash)
                VALUES (:id, :setId, :name, :formation, :eaFormation, :eaType, :kind, CAST(:requirements AS jsonb), :target, CAST(:reward AS jsonb), :hash)
                ON CONFLICT (id) DO UPDATE SET set_id = EXCLUDED.set_id, name = EXCLUDED.name, formation = EXCLUDED.formation, ea_formation = EXCLUDED.ea_formation,
                  ea_type = EXCLUDED.ea_type, kind = EXCLUDED.kind, requirements = EXCLUDED.requirements, item_score_target = EXCLUDED.item_score_target,
                  reward = EXCLUDED.reward, content_hash = EXCLUDED.content_hash
                """, new MapSqlParameterSource().addValue("id", id).addValue("setId", setId).addValue("name", name).addValue("formation", formation)
                .addValue("eaFormation", eaFormation).addValue("eaType", eaType)
                .addValue("kind", kind).addValue("requirements", json(requirements)).addValue("target", itemScoreTarget).addValue("reward", json(reward)).addValue("hash", hash));
    }

    public void upsertObjectiveGroup(long id, short gameVersion, String name, String gameMode, Instant startsAt, Instant endsAt, JsonNode reward, JsonNode objectives, String hash) {
        jdbc.update("""
                INSERT INTO objective_group (id, game_version_id, name, game_mode, starts_at, ends_at, reward, objectives, status, content_hash)
                VALUES (:id, :gv, :name, :mode, :startsAt, :endsAt, CAST(:reward AS jsonb), CAST(:objectives AS jsonb), 'CONFIRMED', :hash)
                ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, game_mode = EXCLUDED.game_mode, starts_at = COALESCE(EXCLUDED.starts_at, objective_group.starts_at),
                  ends_at = COALESCE(EXCLUDED.ends_at, objective_group.ends_at), reward = EXCLUDED.reward, objectives = EXCLUDED.objectives,
                  status = 'CONFIRMED', content_hash = EXCLUDED.content_hash
                """, new MapSqlParameterSource().addValue("id", id).addValue("gv", gameVersion).addValue("name", name).addValue("mode", gameMode)
                .addValue("startsAt", ts(startsAt)).addValue("endsAt", ts(endsAt)).addValue("reward", json(reward)).addValue("objectives", json(objectives)).addValue("hash", hash));
    }

    public void upsertEvolution(long id, short gameVersion, String name, JsonNode cost, JsonNode requirements, JsonNode levels, Instant endsAt, String hash) {
        jdbc.update("""
                INSERT INTO evolution (id, game_version_id, name, cost, requirements, levels, ends_at, status, content_hash)
                VALUES (:id, :gv, :name, CAST(:cost AS jsonb), CAST(:requirements AS jsonb), CAST(:levels AS jsonb), :endsAt, 'CONFIRMED', :hash)
                ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, cost = EXCLUDED.cost, requirements = EXCLUDED.requirements, levels = EXCLUDED.levels,
                  ends_at = COALESCE(EXCLUDED.ends_at, evolution.ends_at), status = 'CONFIRMED', content_hash = EXCLUDED.content_hash
                """, new MapSqlParameterSource().addValue("id", id).addValue("gv", gameVersion).addValue("name", name).addValue("cost", json(cost))
                .addValue("requirements", json(requirements)).addValue("levels", json(levels)).addValue("endsAt", ts(endsAt)).addValue("hash", hash));
    }

    public void upsertPromo(short gameVersion, String code, String name, Instant startsAt, Instant endsAt) {
        jdbc.update("""
                INSERT INTO ut_promo (game_version_id, code, name, starts_at, ends_at) VALUES (:gv, :code, :name, :s, :e)
                ON CONFLICT (game_version_id, code) DO UPDATE SET name = EXCLUDED.name, starts_at = EXCLUDED.starts_at, ends_at = EXCLUDED.ends_at
                """, new MapSqlParameterSource().addValue("gv", gameVersion).addValue("code", code).addValue("name", name).addValue("s", ts(startsAt)).addValue("e", ts(endsAt)));
    }

    private static final java.util.Set<String> TOUCHABLE = java.util.Set.of("sbc_set", "objective_group", "evolution");

    /** İçerik aynı kaldığında da son görülme ve (biliniyorsa) bitiş zamanı yenilenir. */
    public void touch(String table, long id, Instant endsAt) {
        if (!TOUCHABLE.contains(table)) {
            return;
        }
        jdbc.update("UPDATE " + safeTable(table) + " SET last_seen_at = now(), ends_at = COALESCE(:e, ends_at) WHERE id = :id", new MapSqlParameterSource().addValue("e", ts(endsAt)).addValue("id", id));
    }

    public Optional<String> challengeKind(long id) {
        return jdbc.queryForList("SELECT kind FROM sbc_challenge WHERE id = :id", Map.of("id", id), String.class).stream().findFirst();
    }

    /** V3-05d-b: anahtar için açık kuyruk kaydı kapanır (içerik aynı çıktı); kayıt silinmez. */
    public void closeOpenQueue(String kind, String naturalKey, String status) {
        jdbc.update("UPDATE contrib_queue SET status = :s, resolved_at = now() WHERE kind = :k AND natural_key = :n AND status = 'OPEN'",
                Map.of("s", status, "k", kind, "n", naturalKey));
    }

    public Optional<String> hashOf(String table, long id) {
        List<String> found = jdbc.queryForList("SELECT content_hash FROM " + safeTable(table) + " WHERE id = :id", Map.of("id", id), String.class);
        return found.stream().findFirst();
    }

    public void setStatus(String table, long id, String status) {
        jdbc.update("UPDATE " + safeTable(table) + " SET status = :s WHERE id = :id", Map.of("s", status, "id", id));
    }

    // ---- okuma ----

    public List<SbcSetDto> sbcSets(short gameVersion, String category, boolean activeOnly, boolean includeChallenges) {
        String sql = "SELECT * FROM sbc_set WHERE game_version_id = :gv AND status <> 'RETIRED'" + (Objects.isNull(category) ? "" : " AND category = :category")
                + (activeOnly ? " AND (ends_at IS NULL OR ends_at > now())" : "") + " ORDER BY COALESCE(ends_at, 'infinity') ASC, id DESC";
        List<SbcSetDto> sets = jdbc.query(sql, new MapSqlParameterSource().addValue("gv", gameVersion).addValue("category", category), setMapper());
        if (includeChallenges) {
            return sets.stream().map(this::withChallenges).toList();
        }
        return sets.stream().map(s -> s.withChallenges(challengeCount(s.id()), null)).toList();
    }

    public Optional<SbcSetDto> sbcSet(long id) {
        return jdbc.query("SELECT * FROM sbc_set WHERE id = :id", Map.of("id", id), setMapper()).stream().findFirst().map(this::withChallenges);
    }

    private SbcSetDto withChallenges(SbcSetDto s) {
        List<SbcChallengeDto> challenges = jdbc.query("SELECT * FROM sbc_challenge WHERE set_id = :id ORDER BY id", Map.of("id", s.id()), (rs, i) -> new SbcChallengeDto(
                rs.getLong("id"), rs.getLong("set_id"), rs.getString("name"), rs.getString("formation"), rs.getString("ea_formation"), rs.getString("ea_type"),
                rs.getString("kind"), read(rs.getString("requirements")), (Integer) rs.getObject("item_score_target"), read(rs.getString("reward"))));
        return s.withChallenges(challenges.size(), challenges);
    }

    private int challengeCount(long setId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM sbc_challenge WHERE set_id = :id", Map.of("id", setId), Integer.class);
        return Objects.isNull(count) ? 0 : count;
    }

    public List<ObjectiveGroupDto> objectiveGroups(short gameVersion, boolean activeOnly) {
        return jdbc.query("SELECT * FROM objective_group WHERE game_version_id = :gv AND status <> 'RETIRED'" + (activeOnly ? " AND (ends_at IS NULL OR ends_at > now())" : "")
                + " ORDER BY COALESCE(ends_at, 'infinity'), id", Map.of("gv", gameVersion), (rs, i) -> new ObjectiveGroupDto(rs.getLong("id"), rs.getString("name"), rs.getString("game_mode"),
                instant(rs.getTimestamp("starts_at")), instant(rs.getTimestamp("ends_at")), read(rs.getString("reward")), read(rs.getString("objectives")), rs.getString("status")));
    }

    public List<EvolutionDto> evolutions(short gameVersion, boolean activeOnly) {
        return jdbc.query("SELECT * FROM evolution WHERE game_version_id = :gv AND status <> 'RETIRED'" + (activeOnly ? " AND (ends_at IS NULL OR ends_at > now())" : "")
                + " ORDER BY COALESCE(ends_at, 'infinity'), id", Map.of("gv", gameVersion), (rs, i) -> new EvolutionDto(rs.getLong("id"), rs.getString("name"), read(rs.getString("cost")),
                read(rs.getString("requirements")), read(rs.getString("levels")), instant(rs.getTimestamp("starts_at")), instant(rs.getTimestamp("ends_at")), rs.getString("status")));
    }

    public List<PromoDto> promos(short gameVersion) {
        return jdbc.query("SELECT * FROM ut_promo WHERE game_version_id = :gv ORDER BY COALESCE(ends_at, 'infinity') DESC, id DESC", Map.of("gv", gameVersion),
                (rs, i) -> new PromoDto(rs.getInt("id"), rs.getString("code"), rs.getString("name"), instant(rs.getTimestamp("starts_at")), instant(rs.getTimestamp("ends_at"))));
    }

    public List<ItemDto> items(short gameVersion, ItemSearch search) {
        List<String> where = new ArrayList<>(List.of("i.game_version_id = :gv", "i.status <> 'RETIRED'"));
        MapSqlParameterSource params = new MapSqlParameterSource("gv", gameVersion).addValue("limit", Math.min(Math.max(search.limit(), 1), 100)).addValue("offset", Math.max(search.offset(), 0));
        if (Objects.nonNull(search.q()) && !search.q().isBlank()) {
            where.add("i.name ILIKE :q ESCAPE '!'");
            params.addValue("q", "%" + search.q().trim().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
        }
        addEq(where, params, "i.rating >= :minRating", "minRating", search.minRating());
        addEq(where, params, "i.rating <= :maxRating", "maxRating", search.maxRating());
        addEq(where, params, "(i.position = :position OR :position = ANY(i.alt_positions))", "position", search.position());
        addEq(where, params, "i.league_id = :leagueId", "leagueId", search.leagueId());
        addEq(where, params, "i.nation_id = :nationId", "nationId", search.nationId());
        addEq(where, params, "i.club_id = :clubId", "clubId", search.clubId());
        addEq(where, params, "i.card_type = :cardType", "cardType", search.cardType());
        if (Objects.nonNull(search.resourceIds()) && !search.resourceIds().isEmpty()) {
            where.add("i.ea_resource_id IN (:resourceIds)");
            params.addValue("resourceIds", search.resourceIds());
        }
        return jdbc.query("SELECT i.*, p.code AS promo_code FROM ut_item i LEFT JOIN ut_promo p ON p.id = i.promo_id WHERE " + String.join(" AND ", where)
                + " ORDER BY i.rating DESC, i.id LIMIT :limit OFFSET :offset", params, itemMapper());
    }

    public Optional<ItemDto> item(long id) {
        return jdbc.query("SELECT i.*, p.code AS promo_code FROM ut_item i LEFT JOIN ut_promo p ON p.id = i.promo_id WHERE i.id = :id", Map.of("id", id), itemMapper()).stream().findFirst();
    }

    /** Aynı oyuncunun tüm kart versiyonları (versiyon şeridi, UT-19). */
    public List<ItemDto> versionsOf(long eaAssetId, short gameVersion) {
        return jdbc.query("SELECT i.*, p.code AS promo_code FROM ut_item i LEFT JOIN ut_promo p ON p.id = i.promo_id WHERE i.ea_asset_id = :a AND i.game_version_id = :gv ORDER BY i.rating DESC",
                Map.of("a", eaAssetId, "gv", gameVersion), itemMapper());
    }

    public Optional<Long> itemIdByResource(long eaResourceId, short gameVersion) {
        return jdbc.queryForList("SELECT id FROM ut_item WHERE ea_resource_id = :r AND game_version_id = :gv", Map.of("r", eaResourceId, "gv", gameVersion), Long.class).stream().findFirst();
    }

    public int itemCount(short gameVersion) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM ut_item WHERE game_version_id = :gv", Map.of("gv", gameVersion), Integer.class);
        return Objects.isNull(count) ? 0 : count;
    }

    public int syncBaseItems(short gameVersion) {
        return jdbc.update("""
                INSERT INTO ut_item (game_version_id, ea_asset_id, ea_resource_id, base_player_id, name, rating, position, alt_positions, club_id, league_id, nation_id,
                                     card_type, is_rare, weak_foot, skill_moves, accelerate, source, status, updated_at)
                SELECT r.game_version_id, b.ea_resource_id, b.ea_resource_id, b.id, b.full_name, r.overall, r.positions[1], COALESCE(r.positions[2:], '{}'),
                       c.ea_team_id, l.ea_league_id, n.ea_nation_id, 'NORMAL', false, r.weak_foot, r.skill_moves, r.accelerate, 'EA_SITE', 'CONFIRMED', now()
                FROM base_player b JOIN player_rating r ON r.base_player_id = b.id AND r.game_version_id = :gv LEFT JOIN club c ON c.id = r.club_id
                LEFT JOIN league l ON l.id = c.league_id LEFT JOIN nationality n ON n.id = b.nationality_id
                WHERE b.ea_resource_id IS NOT NULL AND b.owner_career_id IS NULL
                ON CONFLICT (game_version_id, ea_resource_id) DO UPDATE SET name = EXCLUDED.name, base_player_id = EXCLUDED.base_player_id, rating = EXCLUDED.rating,
                  position = EXCLUDED.position, alt_positions = EXCLUDED.alt_positions, club_id = EXCLUDED.club_id, league_id = EXCLUDED.league_id, nation_id = EXCLUDED.nation_id,
                  weak_foot = EXCLUDED.weak_foot, skill_moves = EXCLUDED.skill_moves, accelerate = EXCLUDED.accelerate, updated_at = now()
                """, Map.of("gv", gameVersion));
    }

    /** V3-05d: verilen EA kaynak kimliklerinin son gözlenen fiyatı (tek sorgu); gözlemi olmayan kimlik sonuçta yok. */
    public List<KbModels.LatestPrice> latestPrices(Collection<Long> resourceIds, short gameVersion, String platform) {
        return jdbc.query("""
                SELECT DISTINCT ON (i.ea_resource_id) i.ea_resource_id, COALESCE(a.p50, a.min_bin) AS price, a.bucket, a.low_confidence
                  FROM ut_item i JOIN price_agg a ON a.ut_item_id = i.id AND a.platform = :p
                 WHERE i.ea_resource_id IN (:ids) AND i.game_version_id = :gv AND COALESCE(a.p50, a.min_bin) IS NOT NULL
                 ORDER BY i.ea_resource_id, a.bucket DESC""",
                new MapSqlParameterSource().addValue("ids", resourceIds).addValue("gv", gameVersion).addValue("p", platform),
                (rs, n) -> new KbModels.LatestPrice(rs.getLong("ea_resource_id"), rs.getInt("price"), instant(rs.getTimestamp("bucket")), rs.getBoolean("low_confidence")));
    }

    public List<PricePoint> prices(long itemId, String platform, Instant since) {
        return jdbc.query("SELECT bucket, p10, p50, min_bin, samples, low_confidence FROM price_agg WHERE ut_item_id = :i AND platform = :p AND bucket >= :s ORDER BY bucket",
                new MapSqlParameterSource().addValue("i", itemId).addValue("p", platform).addValue("s", ts(since)),
                (rs, n) -> new PricePoint(instant(rs.getTimestamp("bucket")), (Integer) rs.getObject("p10"), (Integer) rs.getObject("p50"), (Integer) rs.getObject("min_bin"), rs.getInt("samples"), rs.getBoolean("low_confidence")));
    }

    /** Fiyat yakalama sayaçları: kayıt edilen ve atlanan (bilinmeyen item / resourceId yok) gözlemler, UTC gün bazında. */
    public void addPriceStats(java.time.LocalDate day, int recorded, int unknownItem, int noResource, int clockSkew) {
        jdbc.update("""
                INSERT INTO price_capture_stat (day, recorded, skipped_unknown_item, skipped_no_resource, clock_skew) VALUES (:d, :r, :u, :n, :s)
                ON CONFLICT (day) DO UPDATE SET recorded = price_capture_stat.recorded + EXCLUDED.recorded,
                  skipped_unknown_item = price_capture_stat.skipped_unknown_item + EXCLUDED.skipped_unknown_item,
                  skipped_no_resource = price_capture_stat.skipped_no_resource + EXCLUDED.skipped_no_resource,
                  clock_skew = price_capture_stat.clock_skew + EXCLUDED.clock_skew
                """, new MapSqlParameterSource().addValue("d", java.sql.Date.valueOf(day)).addValue("r", recorded).addValue("u", unknownItem).addValue("n", noResource).addValue("s", clockSkew));
    }

    public List<Map<String, Object>> priceStats() {
        return jdbc.query("SELECT day, recorded, skipped_unknown_item, skipped_no_resource, clock_skew FROM price_capture_stat ORDER BY day DESC LIMIT 30", (rs, n) -> Map.of(
                "day", rs.getDate("day").toString(), "recorded", rs.getInt("recorded"), "skippedUnknownItem", rs.getInt("skipped_unknown_item"), "skippedNoResource", rs.getInt("skipped_no_resource"), "clockSkew", rs.getInt("clock_skew")));
    }

    /** V3-05d-c: aynı doğal anahtarlı gözlem ikinci kez yazılmaz (ux_price_obs_natural); yazıldıysa true. */
    public boolean insertPriceObservation(long itemId, String platform, Instant at, String kind, int value, long contributorId) {
        return jdbc.update("INSERT INTO price_obs (ut_item_id, platform, observed_at, kind, value, contributor_id) VALUES (:i, :p, :at, :k, :v, :c) ON CONFLICT DO NOTHING",
                new MapSqlParameterSource().addValue("i", itemId).addValue("p", platform).addValue("at", ts(at)).addValue("k", kind).addValue("v", value).addValue("c", contributorId)) > 0;
    }

    /**
     * Son BIN gözlemlerini saatlik kovalara toplar (BE-BUG-02): aykırı değerler (medyanın 0,4×–3× dışı) atılır; her katkıcının kovadaki gözlemleri önce kendi medyanına iner,
     * kova değeri katkıcı medyanlarının yüzdeliğidir (tek katkıcı gözlem sayısıyla ağırlık kazanamaz). Tek katkıcılı kovada gözlem bazlı yüzdelik alınır ve low_confidence işaretlenir.
     */
    public int aggregatePrices(Instant from) {
        return jdbc.update("""
                WITH raw AS (
                  SELECT ut_item_id, platform, date_trunc('hour', observed_at) AS bucket, value, contributor_id
                  FROM price_obs WHERE observed_at >= :from AND kind = 'BIN'
                ), meds AS (
                  SELECT ut_item_id, platform, bucket, percentile_cont(0.5) WITHIN GROUP (ORDER BY value) AS med FROM raw GROUP BY ut_item_id, platform, bucket
                ), clean AS (
                  SELECT r.* FROM raw r JOIN meds m USING (ut_item_id, platform, bucket) WHERE r.value BETWEEN m.med * 0.4 AND m.med * 3
                ), per_contributor AS (
                  SELECT ut_item_id, platform, bucket, contributor_id, percentile_cont(0.5) WITHIN GROUP (ORDER BY value) AS med, min(value) AS min_bin, count(*) AS samples
                  FROM clean GROUP BY ut_item_id, platform, bucket, contributor_id
                ), buckets AS (
                  SELECT ut_item_id, platform, bucket, count(*) AS contributors FROM per_contributor GROUP BY ut_item_id, platform, bucket
                ), combined AS (
                  SELECT p.ut_item_id, p.platform, p.bucket,
                         percentile_cont(0.1) WITHIN GROUP (ORDER BY p.med)::int AS p10, percentile_cont(0.5) WITHIN GROUP (ORDER BY p.med)::int AS p50,
                         min(p.min_bin) AS min_bin, sum(p.samples)::int AS samples, false AS low_confidence
                  FROM per_contributor p JOIN buckets b USING (ut_item_id, platform, bucket) WHERE b.contributors >= 2
                  GROUP BY p.ut_item_id, p.platform, p.bucket
                ), single AS (
                  SELECT c.ut_item_id, c.platform, c.bucket,
                         percentile_cont(0.1) WITHIN GROUP (ORDER BY c.value)::int AS p10, percentile_cont(0.5) WITHIN GROUP (ORDER BY c.value)::int AS p50,
                         min(c.value) AS min_bin, count(*)::int AS samples, true AS low_confidence
                  FROM clean c JOIN buckets b USING (ut_item_id, platform, bucket) WHERE b.contributors < 2
                  GROUP BY c.ut_item_id, c.platform, c.bucket
                )
                INSERT INTO price_agg (ut_item_id, platform, bucket, p10, p50, min_bin, samples, low_confidence)
                SELECT * FROM combined UNION ALL SELECT * FROM single
                ON CONFLICT (ut_item_id, platform, bucket) DO UPDATE SET p10 = EXCLUDED.p10, p50 = EXCLUDED.p50, min_bin = EXCLUDED.min_bin,
                  samples = EXCLUDED.samples, low_confidence = EXCLUDED.low_confidence
                """, new MapSqlParameterSource("from", ts(from)));
    }

    // ---- katkı olayları ve kuyruk ----

    public void insertEvent(long contributorId, String kind, String naturalKey, String hash, JsonNode payload) {
        jdbc.update("INSERT INTO contrib_event (contributor_id, kind, natural_key, content_hash, payload) VALUES (:c, :k, :n, :h, CAST(:p AS jsonb))",
                new MapSqlParameterSource().addValue("c", contributorId).addValue("k", kind).addValue("n", naturalKey).addValue("h", hash).addValue("p", json(payload)));
    }

    public int distinctContributors(String kind, String naturalKey, String hash) {
        Integer count = jdbc.queryForObject("SELECT count(DISTINCT contributor_id) FROM contrib_event WHERE kind = :k AND natural_key = :n AND content_hash = :h AND received_at > now() - interval '30 days'",
                Map.of("k", kind, "n", naturalKey, "h", hash), Integer.class);
        return Objects.isNull(count) ? 0 : count;
    }

    public Optional<JsonNode> latestPayload(String kind, String naturalKey, String hash) {
        return jdbc.queryForList("SELECT payload::text FROM contrib_event WHERE kind = :k AND natural_key = :n AND content_hash = :h ORDER BY id DESC LIMIT 1",
                Map.of("k", kind, "n", naturalKey, "h", hash), String.class).stream().findFirst().map(this::read);
    }

    public void upsertText(String kind, String naturalKey, String locale, String name, String description) {
        jdbc.update("""
                INSERT INTO kb_text (kind, natural_key, locale, name, description) VALUES (:k, :n, :l, :name, :d)
                ON CONFLICT (kind, natural_key, locale) DO UPDATE SET name = EXCLUDED.name, description = EXCLUDED.description
                """, new MapSqlParameterSource().addValue("k", kind).addValue("n", naturalKey).addValue("l", locale).addValue("name", name).addValue("d", description));
    }

    public void openQueue(String kind, String naturalKey, JsonNode candidates) {
        jdbc.update("""
                INSERT INTO contrib_queue (kind, natural_key, candidates) VALUES (:k, :n, CAST(:c AS jsonb))
                ON CONFLICT (kind, natural_key) WHERE status = 'OPEN' DO UPDATE SET candidates = (
                  SELECT jsonb_agg(c ORDER BY ord) FROM (
                    SELECT DISTINCT ON (c ->> 'hash') c, ord FROM jsonb_array_elements(contrib_queue.candidates || EXCLUDED.candidates) WITH ORDINALITY AS t(c, ord)
                    ORDER BY c ->> 'hash', ord) merged)
                """,
                new MapSqlParameterSource().addValue("k", kind).addValue("n", naturalKey).addValue("c", json(candidates)));
    }

    public List<QueueItem> openQueue() {
        return jdbc.query("SELECT id, kind, natural_key, candidates::text AS candidates, created_at FROM contrib_queue WHERE status = 'OPEN' ORDER BY id", queueMapper());
    }

    public Optional<QueueItem> queueItem(long id) {
        return jdbc.query("SELECT id, kind, natural_key, candidates::text AS candidates, created_at FROM contrib_queue WHERE id = :id AND status = 'OPEN'", Map.of("id", id), queueMapper()).stream().findFirst();
    }

    public void closeQueue(long id, String status) {
        jdbc.update("UPDATE contrib_queue SET status = :s, resolved_at = now() WHERE id = :id", Map.of("s", status, "id", id));
    }

    public List<Long> contributorsOf(String kind, String naturalKey, String hash) {
        return jdbc.queryForList("SELECT DISTINCT contributor_id FROM contrib_event WHERE kind = :k AND natural_key = :n AND content_hash = :h", Map.of("k", kind, "n", naturalKey, "h", hash), Long.class);
    }

    // ---- yardımcılar ----

    private RowMapper<QueueItem> queueMapper() {
        return (rs, i) -> new QueueItem(rs.getLong("id"), rs.getString("kind"), rs.getString("natural_key"), read(rs.getString("candidates")), instant(rs.getTimestamp("created_at")));
    }

    private RowMapper<SbcSetDto> setMapper() {
        return (rs, i) -> new SbcSetDto(rs.getLong("id"), rs.getString("name"), rs.getString("description"), rs.getString("category"), (Integer) rs.getObject("repeatable"),
                instant(rs.getTimestamp("starts_at")), instant(rs.getTimestamp("ends_at")), read(rs.getString("reward")), rs.getString("status"), 0, null,
                instant(rs.getTimestamp("first_seen_at")), instant(rs.getTimestamp("content_changed_at")));
    }

    private RowMapper<ItemDto> itemMapper() {
        return (rs, i) -> new ItemDto(rs.getLong("id"), rs.getLong("ea_asset_id"), rs.getLong("ea_resource_id"), rs.getString("name"), rs.getInt("rating"), rs.getString("position"),
                strings(rs, "alt_positions"), (Integer) rs.getObject("club_id"), (Integer) rs.getObject("league_id"), (Integer) rs.getObject("nation_id"), rs.getString("card_type"),
                rs.getBoolean("is_rare"), rs.getString("promo_code"), read(rs.getString("face_stats")), (Integer) rs.getObject("weak_foot"), (Integer) rs.getObject("skill_moves"),
                rs.getString("accelerate"), rs.getBoolean("untradeable_only"), rs.getString("status"), rules.qualityOf(rs.getInt("rating")));
    }

    private static List<String> strings(ResultSet rs, String column) throws SQLException {
        java.sql.Array array = rs.getArray(column);
        return Objects.isNull(array) ? List.of() : Arrays.asList((String[]) array.getArray());
    }

    private static void addEq(List<String> where, MapSqlParameterSource params, String clause, String name, Object value) {
        if (Objects.nonNull(value)) {
            where.add(clause);
            params.addValue(name, value);
        }
    }

    private static String safeTable(String table) {
        return switch (table) {
            case "sbc_set", "sbc_challenge", "objective_group", "evolution" -> table;
            default -> throw new IllegalArgumentException("table");
        };
    }

    private static Timestamp ts(Instant instant) {
        return Objects.isNull(instant) ? null : Timestamp.from(instant);
    }

    private static Instant instant(Timestamp timestamp) {
        return Objects.isNull(timestamp) ? null : timestamp.toInstant();
    }

    private String json(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(Objects.isNull(node) ? objectMapper.nullNode() : node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode read(String value) {
        try {
            return Objects.isNull(value) ? objectMapper.nullNode() : objectMapper.readTree(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
