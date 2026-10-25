package com.fcareer.career.importer;

import com.fcareer.career.entity.CareerEntity;
import com.fcareer.career.model.response.TeamProfileResponse;
import com.fcareer.career.model.response.TeamSummaryResponse;
import com.fcareer.career.service.CareerService;
import com.fcareer.catalog.model.request.PlayerSearchRequest;
import com.fcareer.ingestion.base.BaseCatalogImporter;
import com.fcareer.catalog.model.response.PlayerSummaryResponse;
import com.fcareer.catalog.repository.PlayerSearchRepository;
import com.fcareer.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TeamInsightService {

    private static final String TEAM = "team";
    private static final int ROSTER_LIMIT = 80;
    private static final int TOP_PLAYERS = 18;
    private static final double TOP_PERCENTILE = 0.9;
    private static final double HIGH_PERCENTILE = 0.75;
    private static final double LOW_PERCENTILE = 0.25;
    private static final int LINE_GAP = 3;
    private static final Set<String> FULLBACKS = Set.of("LB", "RB");
    private static final Set<String> MIDFIELD = Set.of("CDM", "CM", "CAM", "LM", "RM");
    private static final Set<String> FORWARDS = Set.of("ST", "LW", "RW");

    private static final String SUMMARY_SQL = """
            SELECT t.team_id, t.name, t.league_name, t.gender, t.overall, t.attack, t.midfield, t.defence,
                   (SELECT count(*) FROM career_player cp WHERE cp.career_id = t.career_id AND cp.team_id = t.team_id) AS players
            FROM career_team t
            WHERE t.career_id = :c AND NOT t.free_agent_pool AND (:q IS NULL OR t.name ILIKE :q)
              -- ACADEMY-b karar 1: adı çözülmemiş ("Takım #id") ve oyuncusu olmayan takım listelenmez.
              AND (t.name NOT LIKE :placeholder OR EXISTS (SELECT 1 FROM career_player cp WHERE cp.career_id = t.career_id AND cp.team_id = t.team_id))
            ORDER BY t.overall DESC NULLS LAST, t.name LIMIT :limit""";

    private static final String PROFILE_SQL = """
            SELECT * FROM (
              SELECT t.*, percent_rank() OVER (PARTITION BY t.gender ORDER BY t.domestic_prestige) AS domestic_pr,
                     percent_rank() OVER (PARTITION BY t.gender ORDER BY t.international_prestige) AS international_pr,
                     percent_rank() OVER (PARTITION BY t.gender ORDER BY t.popularity) AS popularity_pr,
                     percent_rank() OVER (PARTITION BY t.gender ORDER BY t.club_worth) AS worth_pr,
                     percent_rank() OVER (PARTITION BY t.gender ORDER BY t.youth_development) AS youth_pr,
                     percent_rank() OVER (PARTITION BY t.gender ORDER BY t.profitability) AS profit_pr,
                     (SELECT r.name FROM career_team r WHERE r.career_id = t.career_id AND r.team_id = t.rival_team_id) AS rival_name
              FROM career_team t WHERE t.career_id = :c AND NOT t.free_agent_pool) x
            WHERE x.team_id = :t""";

    private final NamedParameterJdbcTemplate jdbc;
    private final CareerService careerService;
    private final PlayerSearchRepository playerSearchRepository;

    @Transactional(readOnly = true)
    public List<TeamSummaryResponse> teams(Long userId, Long careerId, String query, int limit) {
        careerService.requireOwned(userId, careerId);
        String like = Objects.isNull(query) || query.isBlank() ? null : "%" + query.trim() + "%";
        return jdbc.query(SUMMARY_SQL,
                new MapSqlParameterSource().addValue("c", careerId).addValue("q", like, java.sql.Types.VARCHAR).addValue("limit", limit)
                        .addValue("placeholder", BaseCatalogImporter.PLACEHOLDER_TEAM_LABEL + "%"),
                (rs, i) -> TeamSummaryResponse.builder()
                        .teamId(rs.getInt("team_id")).name(rs.getString("name")).league(rs.getString("league_name"))
                        .gender(nullable(rs.getObject("gender"))).overall(nullable(rs.getObject("overall")))
                        .attack(nullable(rs.getObject("attack"))).midfield(nullable(rs.getObject("midfield")))
                        .defence(nullable(rs.getObject("defence"))).playerCount(rs.getInt("players")).build());
    }

    @Transactional(readOnly = true)
    public TeamProfileResponse profile(Long userId, Long careerId, Integer teamId) {
        CareerEntity career = careerService.requireOwned(userId, careerId);
        List<Map<String, Object>> rows = jdbc.queryForList(PROFILE_SQL, Map.of("c", careerId, "t", teamId));
        if (rows.isEmpty()) {
            throw new NotFoundException(TEAM, teamId);
        }
        Map<String, Object> t = rows.get(0);
        List<PlayerSummaryResponse> roster = playerSearchRepository.search(PlayerSearchRequest.builder()
                .gameVersionId(career.getGameVersionId()).careerId(careerId).teamId(teamId)
                .sort("overall").page(0).size(ROSTER_LIMIT).build());
        Integer overall = nullable(t.get("overall"));
        Integer attack = nullable(t.get("attack"));
        Integer midfield = nullable(t.get("midfield"));
        Integer defence = nullable(t.get("defence"));

        List<String> strengths = new ArrayList<>();
        List<String> weaknesses = new ArrayList<>();
        lineInsight("Hücum", attack, overall, strengths, weaknesses);
        lineInsight("Orta saha", midfield, overall, strengths, weaknesses);
        lineInsight("Savunma", defence, overall, strengths, weaknesses);
        depthInsight(roster, weaknesses);
        Double averageAge = averageAge(roster, strengths, weaknesses);

        List<String> culture = new ArrayList<>();
        percentileInsight(t.get("domestic_pr"), "Ülke içinde büyük, köklü kulüp (prestij ilk %10)", culture, TOP_PERCENTILE);
        percentileInsight(t.get("international_pr"), "Uluslararası arenada tanınan kulüp (ilk %10)", culture, TOP_PERCENTILE);
        percentileInsight(t.get("popularity_pr"), "Taraftar tabanı çok geniş; transferde oyuncuları çeker", culture, TOP_PERCENTILE);
        percentileInsight(t.get("worth_pr"), "Zengin kulüp; bütçe ve maaş esnekliği yüksek olma eğiliminde", culture, TOP_PERCENTILE);
        if (percentile(t.get("worth_pr")) <= LOW_PERCENTILE) {
            culture.add("Mali gücü sınırlı; büyük transfer yerine akıllı/ucuz hamle gerekir");
        }
        if (percentile(t.get("youth_pr")) >= HIGH_PERCENTILE) {
            culture.add("Altyapıya önem veren kulüp; genç oyuncu yetiştirmek ve oynatmak kültürün parçası");
        }
        if (percentile(t.get("profit_pr")) >= HIGH_PERCENTILE) {
            culture.add("Finansal olarak kârlı; satışlar yönetim için öncelik olmayabilir");
        }
        if (culture.isEmpty()) {
            culture.add("Belirgin bir kulüp kültürü sinyali yok; orta ölçekli, dengeli bir yapı");
        }

        Integer rivalId = nullable(t.get("rival_team_id"));
        return TeamProfileResponse.builder()
                .team(TeamSummaryResponse.builder().teamId(teamId).name((String) t.get("name")).league((String) t.get("league_name"))
                        .gender(nullable(t.get("gender"))).overall(overall).attack(attack).midfield(midfield).defence(defence)
                        .playerCount(roster.size()).build())
                .rivalTeamId(rivalId == null || rivalId < 0 ? null : rivalId)
                .rivalTeamName((String) t.get("rival_name"))
                .averageAge(averageAge)
                .strengths(strengths).weaknesses(weaknesses).culture(culture)
                .topPlayers(roster.stream().limit(TOP_PLAYERS).toList())
                .build();
    }

    private void lineInsight(String line, Integer value, Integer overall, List<String> strengths, List<String> weaknesses) {
        if (Objects.isNull(value) || Objects.isNull(overall)) {
            return;
        }
        if (value >= overall + LINE_GAP) {
            strengths.add(line + " hattı takımın geri kalanından güçlü (" + value + " / genel " + overall + ")");
        } else if (value <= overall - LINE_GAP) {
            weaknesses.add(line + " hattı takımın geri kalanından zayıf (" + value + " / genel " + overall + ")");
        }
    }

    private void depthInsight(List<PlayerSummaryResponse> roster, List<String> weaknesses) {
        long keepers = roster.stream().filter(p -> "GK".equals(first(p))).count();
        long centreBacks = roster.stream().filter(p -> "CB".equals(first(p))).count();
        long fullbacks = roster.stream().filter(p -> FULLBACKS.contains(first(p))).count();
        long midfielders = roster.stream().filter(p -> MIDFIELD.contains(first(p))).count();
        long forwards = roster.stream().filter(p -> FORWARDS.contains(first(p))).count();
        if (keepers < 2) {
            weaknesses.add("Kaleci derinliği yok (" + keepers + " kaleci)");
        }
        if (centreBacks < 3) {
            weaknesses.add("Stoper sayısı az (" + centreBacks + ")");
        }
        if (fullbacks < 3) {
            weaknesses.add("Bek derinliği zayıf (" + fullbacks + " bek)");
        }
        if (midfielders < 5) {
            weaknesses.add("Orta saha rotasyonu dar (" + midfielders + " oyuncu)");
        }
        if (forwards < 3) {
            weaknesses.add("Forvet seçeneği az (" + forwards + " oyuncu)");
        }
    }

    private Double averageAge(List<PlayerSummaryResponse> roster, List<String> strengths, List<String> weaknesses) {
        double average = roster.stream().limit(TOP_PLAYERS).map(PlayerSummaryResponse::getAge).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).average().orElse(Double.NaN);
        if (Double.isNaN(average)) {
            return null;
        }
        double rounded = Math.round(average * 10) / 10.0;
        if (rounded <= 24.5) {
            strengths.add("Genç kadro (ilk 18 yaş ortalaması " + rounded + "): gelişim potansiyeli yüksek");
            weaknesses.add("Genç kadro: tecrübe ve istikrar eksikliği yaşanabilir");
        } else if (rounded >= 28.5) {
            strengths.add("Tecrübeli kadro (yaş ortalaması " + rounded + ")");
            weaknesses.add("Yaşlı kadro (" + rounded + "): kısa vadede güçlü, birkaç sezon sonra yenilenme gerekir");
        }
        return rounded;
    }

    private void percentileInsight(Object value, String text, List<String> culture, double threshold) {
        if (percentile(value) >= threshold) {
            culture.add(text);
        }
    }

    private static double percentile(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.5;
    }

    private static String first(PlayerSummaryResponse player) {
        return player.getPositions().isEmpty() ? "" : player.getPositions().get(0);
    }

    private static Integer nullable(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }
}
