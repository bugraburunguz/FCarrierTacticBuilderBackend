package com.fcareer.recommendation.pipeline;

import com.fcareer.career.service.CareerService;
import com.fcareer.recommendation.pipeline.model.CareerRecommendationRequest;
import com.fcareer.recommendation.pipeline.model.CareerRecommendationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Yerel gerçek-veri raporu (E3-6b): `fcareer.report.recommendations.enabled=true` ve `.career-id=<id>` ile açılışta 4 "kullanıcı takımı" seviyesi (dev kulüp, orta Championship,
 * Süper Lig, alt lig = kariyerin kendi takımı) için LB/CB/ST/CM önerilerini markdown yazar. Kullanıcı takımı rapor parametresidir; kayıt verisi değişmez.
 * Bütçe varsayımı: takımın en iyi 16 oyuncusunun MODELED piyasa değerleri toplamının %10'u. Varsayılan kapalı; yalnız yerel veritabanında elle çalıştırılır.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "fcareer.report.recommendations.enabled", havingValue = "true")
public class CareerRecommendationReportRunner implements ApplicationRunner {

    private static final List<String> POSITIONS = List.of("LB", "CB", "ST", "CM");
    private static final int LIMIT = 10;
    private static final double BUDGET_SHARE = 0.10;
    private static final int VALUE_TOP = 16;
    private static final double GIANT_WEAK_GAP = -6.0;
    private static final double LOWER_GIANT_GAP = 5.0;
    private static final double WEAK_OVR_GAP = 8.0;
    private static final List<String> STRENGTH_LOOKUP = List.of("Celtic", "BSC Young Boys", "FC Basel 1893", "AS Saint-Étienne");

    private record Team(int id, String label, String name, String league) {
    }

    private final CareerRecommender recommender;
    private final CareerService careerService;
    private final ClubStrengthService clubStrengthService;
    private final JdbcTemplate jdbc;

    @org.springframework.beans.factory.annotation.Value("${fcareer.report.recommendations.career-id}")
    private long careerId;
    @org.springframework.beans.factory.annotation.Value("${fcareer.report.recommendations.output:/tmp/e3-6b-oneriler.md}")
    private String output;
    @org.springframework.beans.factory.annotation.Value("${fcareer.report.recommendations.timing:false}")
    private boolean timingOnly;
    /** Süre ölçümünün mevkileri (V3-06c: ≥ 10 ihtiyaç); varsayılan rapor mevkileri. */
    @org.springframework.beans.factory.annotation.Value("${fcareer.report.recommendations.timing-positions:LB,CB,ST,CM}")
    private List<String> timingPositions;

    @Override
    public void run(ApplicationArguments args) throws IOException {
        Long userId = jdbc.queryForObject("SELECT user_id FROM career WHERE id = ?", Long.class, careerId);
        CareerService.CareerSquad own = careerService.squadPlayerIds(userId, careerId);
        if (timingOnly) {
            timing(own);
            return;
        }
        ClubStrength strength = clubStrengthService.of(careerId);
        StringBuilder md = new StringBuilder("# E3-6b — Öneriler, 4 kullanıcı takımı seviyesi (gerçek kariyer verisi)\n\n");
        md.append("Kullanıcı takımı **rapor parametresidir**; kayıt verisi değişmedi. Her takım için 4 slot (LB, CB, ST, CM), takımın mevcut kadrosundaki en uygun rol (yoksa mevki varsayılanı), "
                + "profil BALANCED. Bütçe varsayımı: **takımın en iyi 16 oyuncusunun MODELED piyasa değerleri toplamının %10'u**; ayrıca \"bütçe bilinmiyor\" koşusu.\n\n");
        md.append(strengthTable(strength));
        List<Team> teams = teams(strength);
        List<String> flags = new ArrayList<>();
        StringBuilder summary = new StringBuilder("## Takım özeti\n\n| Takım | Bütçe | Kulüp gücü | Ort. yaş | Doğal mevki | ALINABİLİR/ZOR/HAYAL | Önerilenlerin ort. kulüp gücü | Fark |\n|---|---|---|---|---|---|---|---|\n");
        StringBuilder detail = new StringBuilder();
        for (Team team : teams) {
            Long worth = jdbc.queryForObject("""
                    SELECT COALESCE(sum(v), 0) FROM (SELECT COALESCE(pv.value_eur, 0) AS v FROM career_player cp
                      LEFT JOIN player_valuation pv ON pv.base_player_id = cp.player_id AND pv.game_version_id = ? AND pv.source = 'MODELED' AND pv.owner_user_id IS NULL
                     WHERE cp.career_id = ? AND cp.live AND cp.team_id = ? ORDER BY cp.ovr DESC LIMIT ?) t""", Long.class, own.gameVersionId(), careerId, team.id(), VALUE_TOP);
            long budget = Math.round(worth * BUDGET_SHARE);
            List<Long> ids = jdbc.queryForList("SELECT player_id FROM career_player WHERE career_id = ? AND live AND team_id = ?", Long.class, careerId, team.id());
            CareerService.CareerSquad squad = new CareerService.CareerSquad(ids, own.gameVersionId(), budget > 0 ? budget : null, own.gender(), null, careerId, ids);
            Double teamStrength = strength.clubStrength("T:" + team.id());
            detail.append(String.format(Locale.ROOT, "## %s — %s (%s)\n\nKulüp gücü %s · lig gücü %s · kadro %d · varsayılan bütçe %s\n\n", team.label(), team.name(), team.league(), fmt(teamStrength),
                    fmt(strength.leagueStrength(team.league())), ids.size(), budget > 0 ? String.format(Locale.ROOT, "%.1f M€", budget / 1e6) : "bilinmiyor"));
            List<CareerRecommendationResponse.Item> all = new ArrayList<>();
            for (String position : POSITIONS) {
                CareerRecommendationResponse response = recommender.compute(squad, request(position, team.id()));
                detail.append(table(position, response));
                all.addAll(response.getItems());
            }
            CareerService.CareerSquad unknown = new CareerService.CareerSquad(ids, own.gameVersionId(), null, own.gender(), null, careerId, ids);
            detail.append("### Bütçe bilinmiyor koşusu\n\n");
            List<CareerRecommendationResponse.Item> noBudget = new ArrayList<>();
            for (String position : POSITIONS) {
                CareerRecommendationResponse response = recommender.compute(unknown, request(position, team.id()));
                noBudget.addAll(response.getItems());
                detail.append(String.format(Locale.ROOT, "- %s: %s%n", position, response.getItems().stream().map((i) -> i.getPlayer().getName() + " (" + i.getBucketLabel() + ")").collect(Collectors.joining(", "))));
            }
            detail.append('\n');
            summary.append(summaryRow(team, budget, teamStrength, all, flags));
            if (!noBudget.isEmpty()) {
                summary.append(summaryRow(new Team(team.id(), team.label() + " (bütçe bilinmiyor)", team.name(), team.league()), 0, teamStrength, noBudget, new ArrayList<>()));
            }
            all.stream().filter((i) -> i.getBucket() == FeasibilityBucket.HAYAL).forEach((i) -> flags.add(team.label() + ": ana listede HAYAL: " + i.getPlayer().getName()));
            noBudget.stream().filter((i) -> i.getBucket() == FeasibilityBucket.HAYAL).forEach((i) -> flags.add(team.label() + " (bütçe bilinmiyor): ana listede HAYAL: " + i.getPlayer().getName()));
            Double avgGap = all.stream().map(CareerRecommendationResponse.Item::getClubStrengthGap).filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
            if (teamStrength != null && team.label().startsWith("Dev") && avgGap < GIANT_WEAK_GAP) {
                flags.add(team.label() + ": önerilenler ortalama " + fmt(avgGap) + " kulüp gücü farkıyla zayıf kulüp yedekleri");
            }
            if (team.label().startsWith("Dev")) {
                all.stream().filter((i) -> i.getOvrGap() != null && i.getOvrGap() < -WEAK_OVR_GAP)
                        .forEach((i) -> flags.add(team.label() + ": " + i.getPlayer().getName() + " ilk 11 ortalamasının " + fmt(-i.getOvrGap()) + " OVR altında (yedek kalitesi)"));
            }
            if (team.label().startsWith("Alt lig")) {
                all.stream().filter((i) -> i.getBucket() == FeasibilityBucket.ALINABILIR && i.getClubStrengthGap() != null && i.getClubStrengthGap() > LOWER_GIANT_GAP)
                        .forEach((i) -> flags.add(team.label() + ": " + i.getPlayer().getName() + " (" + i.getPlayer().getClub() + ", fark " + fmt(i.getClubStrengthGap()) + ") ALINABİLİR görünüyor"));
            }
        }
        md.append(summary).append("\n## Sağlık denetimi\n\n");
        md.append(flags.isEmpty() ? "Bayrak yok.\n\n" : flags.stream().map((f) -> "- ⚠ " + f).collect(Collectors.joining("\n", "", "\n\n")));
        md.append(detail);
        Files.writeString(Path.of(output), md.toString(), StandardCharsets.UTF_8);
        log.info("Career recommendation report written path : {}", output);
    }

    /** Hat içi süre (HTTP hariç): kariyerin kendi kadrosu ve varsayılan istek; ilk çağrı soğuk (kulüp gücü hesabı dahil), sonrası ısınma 2 + 20 ölçüm. */
    private void timing(CareerService.CareerSquad squad) throws IOException {
        StringBuilder md = new StringBuilder(String.format(Locale.ROOT, "# Öneri süresi — kariyer %d\n\n| Mevki | Soğuk (ms) | p50 | p95 | en yavaş |\n|---|---|---|---|---|\n", careerId));
        List<Long> all = new ArrayList<>();
        for (String position : timingPositions) {
            CareerRecommendationRequest request = requestFor(position);
            long cold = timed(squad, request);
            List<Long> runs = new ArrayList<>();
            for (int i = 0; i < 22; i++) {
                long ms = timed(squad, request);
                if (i >= 2) {
                    runs.add(ms);
                }
            }
            all.addAll(runs);
            long[] sorted = runs.stream().mapToLong(Long::longValue).sorted().toArray();
            md.append(String.format(Locale.ROOT, "| %s | %d | %d | %d | %d |%n", position, cold, sorted[sorted.length / 2], sorted[(int) Math.floor(sorted.length * 0.95)], sorted[sorted.length - 1]));
        }
        long[] sorted = all.stream().mapToLong(Long::longValue).sorted().toArray();
        md.append(String.format(Locale.ROOT, "%nTüm mevkiler: p50 %d ms · p95 %d ms · en yavaş %d ms (n=%d)%n", sorted[sorted.length / 2], sorted[(int) Math.floor(sorted.length * 0.95)], sorted[sorted.length - 1], sorted.length));
        Files.writeString(Path.of(output), md.toString(), StandardCharsets.UTF_8);
        log.info("Career recommendation timing written path : {}", output);
    }

    private static CareerRecommendationRequest requestFor(String position) {
        CareerRecommendationRequest request = new CareerRecommendationRequest();
        request.setPosition(position);
        request.setLimit(LIMIT);
        return request;
    }

    private long timed(CareerService.CareerSquad squad, CareerRecommendationRequest request) {
        long start = System.nanoTime();
        recommender.compute(squad, request);
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static CareerRecommendationRequest request(String position, int teamId) {
        CareerRecommendationRequest request = new CareerRecommendationRequest();
        request.setPosition(position);
        request.setLimit(LIMIT);
        request.setUserTeamId(teamId);
        return request;
    }

    private String strengthTable(ClubStrength strength) {
        StringBuilder sb = new StringBuilder("## Kulüp gücü (en iyi 16 oyuncunun OVR ortalaması)\n\n| Kulüp | Lig | Kulüp gücü | Lig gücü (medyan) |\n|---|---|---|---|\n");
        List<String> names = new ArrayList<>(STRENGTH_LOOKUP);
        names.add("Crewe Alexandra");
        for (String name : names) {
            jdbc.query("SELECT team_id, name, league_name FROM career_team WHERE career_id = ? AND name = ? AND gender = (SELECT gender FROM career_team WHERE career_id = ? AND team_id = 121)",
                    (rs) -> {
                        String key = "T:" + rs.getInt("team_id");
                        sb.append(String.format(Locale.ROOT, "| %s | %s | %s | %s |%n", rs.getString("name"), rs.getString("league_name"), fmt(strength.clubStrength(key)), fmt(strength.leagueStrength(rs.getString("league_name")))));
                    }, careerId, name, careerId);
        }
        return sb.append('\n').toString();
    }

    /** Dev kulüp: Real Madrid; orta Championship ve Süper Lig: ligin medyan güçteki takımı; alt lig: Crewe. */
    private List<Team> teams(ClubStrength strength) {
        List<Team> teams = new ArrayList<>();
        teams.add(byName("Dev kulüp", "Real Madrid", "LALIGA EA SPORTS"));
        teams.add(median(strength, "Orta Championship", "EFL Championship"));
        teams.add(median(strength, "Süper Lig", "Trendyol Süper Lig"));
        teams.add(byName("Alt lig", "Crewe Alexandra", "EFL League Two"));
        return teams;
    }

    private Team byName(String label, String name, String league) {
        return jdbc.queryForObject("SELECT team_id FROM career_team WHERE career_id = ? AND name = ? AND league_name = ?",
                (rs, i) -> new Team(rs.getInt(1), label, name, league), careerId, name, league);
    }

    private Team median(ClubStrength strength, String label, String league) {
        List<Team> inLeague = jdbc.query("SELECT team_id, name FROM career_team WHERE career_id = ? AND league_name = ?", (rs, i) -> new Team(rs.getInt(1), label, rs.getString(2), league), careerId, league)
                .stream().filter((t) -> strength.clubStrength("T:" + t.id()) != null).sorted(Comparator.comparingDouble((Team t) -> strength.clubStrength("T:" + t.id()))).toList();
        return inLeague.get(inLeague.size() / 2);
    }

    private String summaryRow(Team team, long budget, Double teamStrength, List<CareerRecommendationResponse.Item> items, List<String> flags) {
        double age = items.stream().map((i) -> i.getPlayer().getAge()).filter(Objects::nonNull).mapToInt(Integer::intValue).average().orElse(Double.NaN);
        long natural = items.stream().filter((i) -> i.getPositionLabel() == null).count();
        Map<FeasibilityBucket, Long> buckets = new EnumMap<>(FeasibilityBucket.class);
        items.forEach((i) -> buckets.merge(i.getBucket(), 1L, Long::sum));
        double gap = items.stream().map(CareerRecommendationResponse.Item::getClubStrengthGap).filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
        double clubs = items.stream().map(CareerRecommendationResponse.Item::getClubStrength).filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
        return String.format(Locale.ROOT, "| %s (%s) | %s | %s | %.1f | %d/%d | %d/%d/%d | %.1f | %+.1f |%n", team.label(), team.name(), budget > 0 ? String.format(Locale.ROOT, "%.1f M€", budget / 1e6) : "bilinmiyor",
                fmt(teamStrength), age, natural, items.size(), buckets.getOrDefault(FeasibilityBucket.ALINABILIR, 0L), buckets.getOrDefault(FeasibilityBucket.ZOR, 0L),
                buckets.getOrDefault(FeasibilityBucket.HAYAL, 0L), clubs, gap);
    }

    private static String table(String position, CareerRecommendationResponse response) {
        StringBuilder sb = new StringBuilder("### ").append(position).append(" — rol kaynağı ").append(response.getRoleSource()).append(response.getRoleId() == null ? "" : " (" + response.getRoleId() + ")").append("\n\n");
        if (response.getCurrent() != null) {
            sb.append(String.format(Locale.ROOT, "Mevcut: **%s** (currentFit %.1f, rol %s)\n\n", response.getCurrent().getPlayerName(), response.getCurrent().getCurrentFit(), response.getCurrent().getRoleId()));
        } else {
            sb.append("Mevcut: kadroda bu mevkiyi oynayabilen oyuncu yok\n\n");
        }
        sb.append("| # | Oyuncu | Yaş | Kulüp (lig) | Kulüp gücü (fark) | OVR (fark) | Değer | Kova (puan) | currentFit | levelFit | Δ mevcut | futureFit | Mevki | Etiket | 3 artı | 2 eksi | Kova gerekçesi |\n|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        int n = 1;
        for (CareerRecommendationResponse.Item item : response.getItems()) {
            sb.append(String.format(Locale.ROOT, "| %d | %s | %s | %s (%s) | %s (%s) | %d (%s) | %s | %s (%d) | %.1f | %s | %s | %s | %s | %s | %s | %s | %s |%n", n++, item.getPlayer().getName(),
                    item.getPlayer().getAge() == null ? "?" : item.getPlayer().getAge(), nz(item.getPlayer().getClub()), nz(item.getPlayer().getLeague()),
                    fmt(item.getClubStrength()), item.getClubStrengthGap() == null ? "—" : String.format(Locale.ROOT, "%+.1f", item.getClubStrengthGap()),
                    item.getPlayer().getOverall(), item.getOvrGap() == null ? "—" : String.format(Locale.ROOT, "%+.1f", item.getOvrGap()),
                    item.getPlayer().getValueEur() == null ? "—" : String.format(Locale.ROOT, "%.1f M€", item.getPlayer().getValueEur() / 1_000_000.0),
                    item.getBucketLabel(), item.getBucketPoints(), item.getCurrentFit(), item.getLevelFit() == null ? "—" : String.format(Locale.ROOT, "%.1f", item.getLevelFit()),
                    item.getDeltaVsCurrent() == null ? "—" : String.format(Locale.ROOT, "%+.1f", item.getDeltaVsCurrent()),
                    item.getFutureFit() == null ? "—" : String.format(Locale.ROOT, "%.1f", item.getFutureFit()), nz(item.getPositionLabel()), String.join(",", item.getTags()), contributions(item.getPros()), contributions(item.getCons()),
                    item.getBucketFactors().stream().map((f) -> f.name() + " " + f.value() + " (" + f.points() + ")").collect(Collectors.joining("; "))));
        }
        return sb.append('\n').toString();
    }

    private static String contributions(List<com.fcareer.fitengine.service.FitEngineV3.Contribution> list) {
        return list.stream().map((c) -> c.key() + " " + String.format(Locale.ROOT, "%+.1f", c.points())).collect(Collectors.joining(", "));
    }

    private static String fmt(Double value) {
        return Objects.isNull(value) || value.isNaN() ? "—" : String.format(Locale.ROOT, "%.1f", value);
    }

    private static String nz(String value) {
        return value == null ? "—" : value;
    }
}
