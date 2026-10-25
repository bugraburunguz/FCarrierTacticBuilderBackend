package com.fcareer.career.importer;

import com.fcareer.billing.service.EntitlementService;
import com.fcareer.career.academy.AcademyDetector;
import com.fcareer.career.entity.CareerEntity;
import com.fcareer.career.model.response.ImportSummaryResponse;
import com.fcareer.career.service.CareerService;
import com.fcareer.common.exception.ImportParseException;
import com.fcareer.ingestion.base.BaseCatalogImporter;
import com.fcareer.ingestion.base.BaseUploads;
import com.fcareer.ingestion.base.LeExportV2Parser;
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
import java.util.Optional;
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
    /** LE betiğinin DONE satırındaki "bu tablo oyunda yok" notu (ör. career_users); veri eksikliği değil, kullanıcıya gösterilmez. */
    private static final String MISSING_TABLE_NOTE = "MISSINGTABLE ";

    private final NamedParameterJdbcTemplate jdbc;
    private final CareerService careerService;
    private final EntitlementService entitlementService;
    private final BaseCatalogImporter baseCatalogImporter;
    private final com.fcareer.ingestion.base.CareerPlayerWriter careerPlayerWriter;
    private final com.fcareer.career.desk.CareerDeskService careerDeskService;

    @Transactional
    public ImportSummaryResponse importData(Long userId, Long careerId, MultipartFile logFile, Map<String, MultipartFile> csvs) {
        entitlementService.requirePremium(userId);
        CareerEntity career = careerService.requireOwned(userId, careerId);

        BaseUploads.Upload upload = BaseUploads.toUpload(logFile, csvs);
        Map<String, List<Map<String, String>>> tables = upload.tables();
        LeExportV2Parser.LeContext context = upload.context();
        Map<Integer, String> leagueNames = new HashMap<>();
        Set<Integer> internationalLeagues = new HashSet<>();
        readLeagues(tables.get("leagues"), leagueNames, internationalLeagues);
        internationalLeagues.addAll(BaseCatalogImporter.NATIONAL_LEAGUE_IDS);
        Map<Integer, Integer> teamLeague = readLeagueLinks(tables.get("leagueteamlinks"));
        Map<Integer, Map<String, String>> teamRows = readTeams(tables.get("teams"));
        Set<Integer> nationalTeams = new HashSet<>();
        Set<Integer> freeAgentTeams = new HashSet<>(FREE_AGENT_TEAM_IDS);
        teamLeague.forEach((team, league) -> {
            if (internationalLeagues.contains(league)) {
                nationalTeams.add(team);
            }
            if (league == BaseCatalogImporter.FREE_AGENT_LEAGUE_ID) {
                freeAgentTeams.add(team);
            }
        });
        Map<Long, Integer> playerTeam = readLinks(tables.get("teamplayerlinks"), nationalTeams, freeAgentTeams);
        Integer userTeam = Objects.nonNull(context) && Objects.nonNull(context.userTeamId()) ? context.userTeamId()
                : jdbc.queryForObject("SELECT ea_team_id FROM club WHERE id = :club", Map.of("club", career.getClubId()), Integer.class);
        Set<Long> returnedFromLoan = new HashSet<>();
        Map<Long, Integer> loanedFrom = loanSources(tables, career.getGameVersionId(), playerTeam, userTeam, freeAgentTeams, returnedFromLoan);
        Map<Long, Long> eaToBase = eaIdMap();
        Map<Long, Long> generated = baseCatalogImporter.upsertCareerPlayers(careerId, tables, career.getGameVersionId());
        eaToBase.putAll(generated);
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
            boolean freeAgent = freeAgentTeams.contains(teamId);
            if (freeAgent) {
                freeAgents++;
            }
            playerRows.add(new MapSqlParameterSource()
                    .addValue("career", careerId).addValue("base", baseId)
                    .addValue("overall", entry.getValue()[0]).addValue("potential", entry.getValue()[1])
                    .addValue("team", teamId).addValue("free", freeAgent)
                    .addValue("loanFrom", loanedFrom.get(entry.getKey()), java.sql.Types.INTEGER));
        }
        if (playerRows.isEmpty()) {
            throw new ImportParseException("no player matched the catalog");
        }

        jdbc.update("DELETE FROM career_player_data WHERE career_id = :c", Map.of("c", careerId));
        jdbc.update("DELETE FROM career_team WHERE career_id = :c", Map.of("c", careerId));
        batch("""
                INSERT INTO career_player_data (career_id, base_player_id, overall, potential, team_id, free_agent, loan_from_team_id)
                VALUES (:career, :base, :overall, :potential, :team, :free, :loanFrom)""", playerRows);

        Set<Long> matchedBaseIds = new HashSet<>();
        playerRows.forEach((row) -> matchedBaseIds.add((Long) row.getValue("base")));
        baseCatalogImporter.saveCareerRatings(careerId, tables.get("players"), eaToBase, matchedBaseIds, generated.keySet(), career.getGameVersionId());

        // ACADEMY-a: altyapı takımı veriden (kodda kimlik yok); adı "{kulüp} Youth Academy", diğer çözülmemiş adlar genel kural.
        AcademyDetector.Detection academy = AcademyDetector.detect(tables, userTeam);
        String userTeamName = Optional.ofNullable(teamRows.get(userTeam)).map((r) -> r.get("teamname")).orElse(null);
        List<SqlParameterSource> teamParams = new ArrayList<>();
        for (Map.Entry<Integer, Map<String, String>> entry : teamRows.entrySet()) {
            Integer teamId = entry.getKey();
            if (nationalTeams.contains(teamId)) {
                continue;
            }
            Map<String, String> row = entry.getValue();
            teamParams.add(new MapSqlParameterSource()
                    .addValue("career", careerId).addValue("team", teamId).addValue("name", AcademyDetector.displayName(row.get("teamname"), teamId, academy.teamId(), userTeamName))
                    .addValue("league", leagueNames.get(teamLeague.get(teamId)))
                    .addValue("gender", toInt(row.get("gender")))
                    .addValue("pool", freeAgentTeams.contains(teamId))
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

        saveAcademy(careerId, academy, tables.get("career_youthplayers"), eaToBase);

        jdbc.update("""
                INSERT INTO career_import (career_id, imported_at, player_count, team_count) VALUES (:c, now(), :p, :t)
                ON CONFLICT (career_id) DO UPDATE SET imported_at = now(), player_count = :p, team_count = :t""",
                new MapSqlParameterSource().addValue("c", careerId).addValue("p", playerRows.size()).addValue("t", teamParams.size()));
        jdbc.update("""
                UPDATE user_player_state s SET dynamic_potential = d.potential, updated_at = now()
                FROM career_player_data d
                WHERE s.career_id = :c AND d.career_id = s.career_id AND d.base_player_id = s.base_player_id""",
                Map.of("c", careerId));

        // Kullanıcı takımı export'un context.userTeamId'sinden gelir (BE-BUG-13); yalnız eski CSV/log yolunda adla tahmin edilir.
        List<Map<String, Object>> guess = Objects.nonNull(context) && Objects.nonNull(context.userTeamId())
                ? jdbc.queryForList("SELECT t.team_id, t.name FROM career_team t WHERE t.career_id = :c AND t.team_id = :t",
                        new MapSqlParameterSource().addValue("c", careerId).addValue("t", context.userTeamId()))
                : List.of();
        if (guess.isEmpty()) {
            guess = jdbc.queryForList("""
                    SELECT t.team_id, t.name FROM career_team t JOIN club c ON c.id = :club
                    WHERE t.career_id = :c AND NOT t.free_agent_pool AND lower(t.name) = lower(c.name) LIMIT 1""",
                    new MapSqlParameterSource().addValue("c", careerId).addValue("club", career.getClubId()));
        }
        // Betik 2.0.2: oyunun transfer bütçesi (oyun içi para birimi); kayıtta yoksa kullanıcının elle girdiği bütçe korunur.
        if (Objects.nonNull(context) && Objects.nonNull(context.transferBudget())) {
            jdbc.update("UPDATE career SET budget_eur = :b WHERE id = :c", Map.of("b", context.transferBudget(), "c", careerId));
        }
        if (Objects.nonNull(context) && Objects.nonNull(context.gameDate())) {
            jdbc.update("UPDATE career SET current_game_date = :d WHERE id = :c", Map.of("d", java.sql.Date.valueOf(context.gameDate()), "c", careerId));
        }
        // Çift yazma: eski overlay tabloları yazıldı; kariyerin tek doğruluk kaynağı career_player onlardan tazelenir (E3-2).
        careerPlayerWriter.syncCareer(careerId);
        // Kiralıktan dönen oyuncunun ana kulübü şu anki kulübüdür (katalog onu ana kayıttaki kiralık kulübünde tutar); hareket sayılmaz.
        if (!returnedFromLoan.isEmpty()) {
            jdbc.update("""
                    UPDATE career_player cp SET base_club_id = cp.club_id FROM base_player b
                     WHERE cp.career_id = :c AND b.id = cp.player_id AND b.owner_career_id IS NULL AND b.ea_resource_id IN (:ids)""",
                    new MapSqlParameterSource().addValue("c", careerId).addValue("ids", returnedFromLoan));
        }
        careerPlayerWriter.applyLeExtras(careerId, tables.get("players"), eaToBase);
        // Kiralık bitiş tarihi: playerloans ve diğer veritabanı kopyaları (betik 2.0.2).
        List<Map<String, String>> allLoans = new ArrayList<>();
        tables.forEach((name, rows) -> {
            if (name.equals("playerloans") || name.startsWith("playerloans_db")) {
                rows.stream().filter(row -> !returnedFromLoan.contains(toLong(row.get("playerid")))).forEach(allLoans::add);
            }
        });
        int contracts = careerPlayerWriter.applyContracts(careerId, tables.get("players"), allLoans, eaToBase);
        log.info("Career import done careerId : {} players : {} teams : {} freeAgents : {} unmatched : {}",
                careerId, playerRows.size(), teamParams.size(), freeAgents, unmatched);
        Integer guessedTeam = guess.isEmpty() ? null : ((Number) guess.get(0).get("team_id")).intValue();
        Integer synced = Objects.isNull(guessedTeam) || squadEditedByUser(careerId) ? null : rebuildSquad(career, careerId, guessedTeam);
        // FAZ 6a-E: görüntü kadro kurulduktan sonra alınır; kadro sayısı, giren/çıkan ve kadro maaşı içe aktarılan kadroyu yansıtır.
        careerDeskService.takeSnapshot(careerId, "IMPORT", Objects.nonNull(upload.exportId()) ? "export " + upload.exportId() : null);
        int skippedNoPosition = baseCatalogImporter.careerPlayersWithoutPositions(tables.get("players"), eaToBase.keySet());
        List<String> warnings = new ArrayList<>();
        if (skippedNoPosition > 0) {
            warnings.add("CAREER_PLAYERS_SKIPPED_NO_POSITIONS");
        }
        boolean inCareer = Objects.isNull(context) || context.inCareer();
        if (!inCareer) {
            warnings.add("NOT_IN_CAREER");
        }
        // FAZ 6a-A: kariyer export'u ama maaş/sözleşme yok (eski betik ya da eksik tablo): Masa'nın finans ve sözleşme alanları boş kalır.
        if (inCareer && contracts == 0) {
            warnings.add("CONTRACTS_MISSING");
        }
        // Betiğin ham tablo notları (MISSINGTABLE …) loga gider; kullanıcıya yalnız anlamlı kodlar.
        for (String warning : upload.warnings()) {
            if (warning.startsWith(MISSING_TABLE_NOTE)) {
                log.info("Career import export note careerId : {} note : {}", careerId, warning);
            } else {
                warnings.add(warning);
            }
        }
        return ImportSummaryResponse.builder()
                .skippedNoPosition(skippedNoPosition).warnings(warnings)
                .gameDate(Objects.isNull(context) ? null : context.gameDate()).contracts(contracts)
                .squadSynced(synced)
                .players(playerRows.size()).generatedPlayers(generated.size()).unmatchedPlayers(unmatched).teams(teamParams.size()).freeAgents(freeAgents)
                .guessedTeamId(guess.isEmpty() ? null : ((Number) guess.get(0).get("team_id")).intValue())
                .guessedTeamName(guess.isEmpty() ? null : (String) guess.get(0).get("name"))
                .build();
    }

    /**
     * ACADEMY-a: altyapı takımı (career.academy_team_id; bulunamadıysa NULL) ve career_youthplayers alanları her içe aktarmada yeniden yazılır.
     * Alanların anlamı doğrulanmadı (verified:false). Oyuncu kimliği EA → base eşlemesiyle (üretilmiş oyuncular kariyere aittir).
     */
    private void saveAcademy(Long careerId, AcademyDetector.Detection academy, List<Map<String, String>> youthRows, Map<Long, Long> eaToBase) {
        jdbc.update("UPDATE career SET academy_team_id = :t WHERE id = :c", new MapSqlParameterSource().addValue("t", academy.teamId(), java.sql.Types.INTEGER)
                .addValue("c", careerId));
        jdbc.update("DELETE FROM career_youth_player WHERE career_id = :c", Map.of("c", careerId));
        List<SqlParameterSource> rows = new ArrayList<>();
        for (Map<String, String> row : Objects.isNull(youthRows) ? List.<Map<String, String>>of() : youthRows) {
            Long baseId = eaToBase.get(toLong(row.get("playerid")));
            if (Objects.nonNull(baseId)) {
                rows.add(new MapSqlParameterSource().addValue("c", careerId).addValue("p", baseId)
                        .addValue("months", toInt(row.get("monthsinsquad")), java.sql.Types.SMALLINT)
                        .addValue("tier", toInt(row.get("playertier")), java.sql.Types.SMALLINT)
                        .addValue("variance", toInt(row.get("potentialvariance")), java.sql.Types.SMALLINT)
                        .addValue("swing", toInt(row.get("swinglowpotential")), java.sql.Types.SMALLINT));
            }
        }
        batch("""
                INSERT INTO career_youth_player (career_id, base_player_id, months_in_squad, player_tier, potential_variance, swing_low_potential)
                VALUES (:c, :p, :months, :tier, :variance, :swing) ON CONFLICT DO NOTHING""", rows);
        log.info("Career academy careerId : {} teamId : {} signal : {} youth : {}", careerId, academy.teamId(), academy.signal(), rows.size());
    }

    /** Kullanıcı kadroyu elle değiştirdiyse (transfer/terfi olayı var) otomatik yeniden kurma yapılmaz; kullanıcı /import/squad ile açıkça ister (BE-BUG-13). */
    private boolean squadEditedByUser(Long careerId) {
        Integer events = jdbc.queryForObject("SELECT count(*) FROM roster_event WHERE career_id = :c", Map.of("c", careerId), Integer.class);
        return Objects.nonNull(events) && events > 0;
    }

    @Transactional
    public int syncSquad(Long userId, Long careerId, Integer teamId) {
        entitlementService.requirePremium(userId);
        CareerEntity career = careerService.requireOwned(userId, careerId);
        careerPlayerWriter.ensureSeeded(careerId);
        Integer count = jdbc.queryForObject("SELECT count(*) FROM career_player WHERE career_id = :c AND team_id = :t",
                Map.of("c", careerId, "t", teamId), Integer.class);
        if (Objects.isNull(count) || count == 0) {
            throw new ImportParseException("team has no imported players");
        }
        int built = rebuildSquad(career, careerId, teamId);
        log.info("Squad synced from import careerId : {} teamId : {} players : {}", careerId, teamId, built);
        return built;
    }

    private int rebuildSquad(CareerEntity career, Long careerId, Integer teamId) {
        jdbc.update("DELETE FROM user_player_state WHERE career_id = :c", Map.of("c", careerId));
        jdbc.update("""
                INSERT INTO user_player_state (career_id, base_player_id, club_id, dynamic_potential, on_loan, loaned_out, acquired_at, updated_at)
                SELECT :c, d.player_id, :club, d.pot,
                       (d.loan_from_team_id IS NOT NULL AND d.team_id = :t AND d.loan_from_team_id <> :t),
                       (d.loan_from_team_id = :t AND d.team_id <> :t), now(), now()
                FROM career_player d WHERE d.career_id = :c AND d.live AND (d.team_id = :t OR d.loan_from_team_id = :t)""",
                new MapSqlParameterSource().addValue("c", careerId).addValue("t", teamId).addValue("club", career.getClubId()));
        Integer count = jdbc.queryForObject("SELECT count(*) FROM user_player_state WHERE career_id = :c", Map.of("c", careerId), Integer.class);
        return Objects.isNull(count) ? 0 : count;
    }

    /**
     * Kiralık kaynakları (betik 2.0.2): önce playerloans, sonra diğer veritabanı kopyaları (playerloans_db*), sonra kadro durumu
     * (fca_squadstatus loanedout=1, oyunun IsPlayerLoanedOut sorgusu). Kariyerde yapılan kiralıklar ilk kopyada olmayabilir. Kadro durumundaki
     * kiralığın kaynak kulübü ana kayıttaki kulüptür; ana kayıtta zaten şu anki takımındaysa (kaynak belirsiz) eklenmez.
     */
    private Map<Long, Integer> loanSources(Map<String, List<Map<String, String>>> tables, short gameVersionId, Map<Long, Integer> playerTeam,
                                           Integer userTeam, Set<Integer> freeAgentTeams, Set<Long> returned) {
        Map<Long, Integer> loans = readLoans(tables.get("playerloans"));
        tables.keySet().stream().filter(name -> name.startsWith("playerloans_db")).sorted()
                .forEach(name -> readLoans(tables.get(name)).forEach(loans::putIfAbsent));
        // Oyun biten kiralığın satırını silmez: kaynağı oyuncunun şu anki takımı olan kiralık bitmiştir (oyuncu ana kulübüne dönmüş).
        loans.entrySet().removeIf(entry -> {
            boolean over = Objects.equals(entry.getValue(), playerTeam.get(entry.getKey()));
            if (over) {
                returned.add(entry.getKey());
            }
            return over;
        });
        List<Long> flagged = new ArrayList<>();
        for (Map<String, String> row : tables.getOrDefault("fca_squadstatus", List.of())) {
            Long playerId = toLong(row.get("playerid"));
            if (Objects.nonNull(playerId) && "1".equals(row.get("loanedout")) && !loans.containsKey(playerId)) {
                flagged.add(playerId);
            }
        }
        if (!flagged.isEmpty()) {
            baseCatalogImporter.baseTeamIds(flagged, gameVersionId).forEach((playerId, baseTeam) -> {
                if (!Objects.equals(baseTeam, playerTeam.get(playerId))) {
                    loans.put(playerId, baseTeam);
                }
            });
        }
        inferUserTeamLoans(tables.get("previousteam"), loans, gameVersionId, playerTeam, userTeam, freeAgentTeams, returned);
        return loans;
    }

    /**
     * Betik 2.0.3: oyun kariyerde yapılan kiralıkları hiçbir tabloya yazmaz (kariyerdeki playerloans ana kayıtla aynı kalır), kalıcı transferde
     * ise previousteam'e oyuncunun ayrıldığı kulübü yazar. Kullanıcı takımına gelen ya da ondan giden oyuncu, previousteam'de ana kayıttaki
     * kulübü görünmüyorsa kiralıktır (kaynak: ana kayıttaki kulüp). Serbest oyuncu kiralanamaz; serbestten gelen ya da serbest kalan transferdir.
     */
    private void inferUserTeamLoans(List<Map<String, String>> previousRows, Map<Long, Integer> loans, short gameVersionId,
                                    Map<Long, Integer> playerTeam, Integer userTeam, Set<Integer> freeAgentTeams, Set<Long> returned) {
        if (Objects.isNull(previousRows) || Objects.isNull(userTeam)) {
            return;
        }
        Map<Long, Integer> previousTeam = new HashMap<>();
        for (Map<String, String> row : previousRows) {
            Long playerId = toLong(row.get("playerid"));
            Integer team = toInt(row.get("previousteamid"));
            if (Objects.nonNull(playerId) && Objects.nonNull(team)) {
                previousTeam.put(playerId, team);
            }
        }
        Set<Long> candidates = new HashSet<>(baseCatalogImporter.basePlayersOfTeam(userTeam, gameVersionId));
        playerTeam.forEach((playerId, team) -> {
            if (Objects.equals(team, userTeam)) {
                candidates.add(playerId);
            }
        });
        candidates.removeAll(loans.keySet());
        candidates.removeAll(returned);
        baseCatalogImporter.baseTeamIds(candidates, gameVersionId).forEach((playerId, baseTeam) -> {
            Integer team = playerTeam.get(playerId);
            boolean moved = Objects.nonNull(team) && !Objects.equals(team, baseTeam);
            Integer previous = previousTeam.get(playerId);
            // Ana katalog oyunun ana kaydından eski olabilir (oyuncu katalogda kulüpte, oyunda serbest): previousteam serbest havuzsa da transferdir.
            boolean free = freeAgentTeams.contains(team) || freeAgentTeams.contains(baseTeam) || freeAgentTeams.contains(previous);
            if (moved && !free && !Objects.equals(previous, baseTeam)) {
                loans.put(playerId, baseTeam);
            }
        });
    }

    private Map<Long, Integer> readLoans(List<Map<String, String>> rows) {
        Map<Long, Integer> loans = new HashMap<>();
        if (Objects.isNull(rows)) {
            return loans;
        }
        for (Map<String, String> row : rows) {
            Long playerId = toLong(row.get("playerid"));
            Integer from = toInt(row.get("teamidloanedfrom"));
            if (Objects.nonNull(playerId) && Objects.nonNull(from)) {
                loans.put(playerId, from);
            }
        }
        return loans;
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

    private Map<Long, Integer> readLinks(List<Map<String, String>> rows, Set<Integer> nationalTeams, Set<Integer> freeAgentTeams) {
        Map<Long, Integer> links = new HashMap<>();
        for (Map<String, String> row : required(rows, "teamplayerlinks")) {
            Long player = toLong(row.get("playerid"));
            Integer team = toInt(row.get("teamid"));
            if (Objects.isNull(player) || Objects.isNull(team) || nationalTeams.contains(team)) {
                continue;
            }
            Integer existing = links.get(player);
            if (Objects.isNull(existing) || (freeAgentTeams.contains(existing) && !freeAgentTeams.contains(team))) {
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
