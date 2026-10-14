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
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Yerel gerçek-veri raporu (E3-6): `fcareer.report.recommendations.enabled=true` ve `.career-id=<id>` ile açılışta o kariyer için mevkilerin ilk 10 önerisini, recall@10'u ve
 * hat süresini (p50/p95) markdown olarak yazar. Varsayılan kapalı; yalnız kullanıcının kendi yerel veritabanında elle çalıştırılır (kimlik doğrulaması atlanır).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "fcareer.report.recommendations.enabled", havingValue = "true")
public class CareerRecommendationReportRunner implements ApplicationRunner {

    private static final List<String> POSITIONS = List.of("LB", "CB", "ST", "CM");
    private static final int WARMUP = 2;
    private static final int TIMED_RUNS = 12;
    private static final int RECALL_K = 10;
    private static final int FULL_POOL = 1_000_000;

    private final CareerRecommender recommender;
    private final CareerService careerService;
    private final JdbcTemplate jdbc;

    @org.springframework.beans.factory.annotation.Value("${fcareer.report.recommendations.career-id}")
    private long careerId;
    @org.springframework.beans.factory.annotation.Value("${fcareer.report.recommendations.output:/tmp/e3-6-oneriler.md}")
    private String output;

    @Override
    public void run(ApplicationArguments args) throws IOException {
        Long userId = jdbc.queryForObject("SELECT user_id FROM career WHERE id = ?", Long.class, careerId);
        CareerService.CareerSquad squad = careerService.squadPlayerIds(userId, careerId);
        StringBuilder md = new StringBuilder("# E3-6 — Crewe önerileri (gerçek kariyer verisi)\n\n");
        md.append(String.format(Locale.ROOT, "Kariyer %d · bütçe %s · kadro %d oyuncu · havuz üst sınırı %s\n\n", careerId, squad.budgetEur() == null ? "bilinmiyor (null)" : squad.budgetEur() + " €",
                squad.ownedIds().size(), "SQL önskoru (OVR sıralaması yok)"));
        List<Double> recalls = new ArrayList<>();
        List<Long> millis = new ArrayList<>();
        for (String position : POSITIONS) {
            CareerRecommendationRequest request = new CareerRecommendationRequest();
            request.setPosition(position);
            request.setLimit(RECALL_K);
            CareerRecommendationResponse response = recommender.compute(squad, request);
            md.append(table(position, response));
            List<Long> full = recommender.rank(squad, request, FULL_POOL).stream().limit(RECALL_K).map((s) -> s.summary().getId()).toList();
            List<Long> pooled = recommender.rank(squad, request, 2000).stream().limit(RECALL_K).map((s) -> s.summary().getId()).toList();
            double recall = full.isEmpty() ? 1.0 : pooled.stream().filter(full::contains).count() / (double) full.size();
            recalls.add(recall);
            md.append(String.format(Locale.ROOT, "\n_recall@10 (önskorlu 2000'lik havuz ↔ tüm kayıttaki oyuncuların tam puanı): %.2f · tam puanlanan: %d_\n\n", recall, response.getScored()));
        }
        for (int i = 0; i < WARMUP + TIMED_RUNS; i++) {
            for (String position : POSITIONS) {
                CareerRecommendationRequest request = new CareerRecommendationRequest();
                request.setPosition(position);
                request.setLimit(RECALL_K);
                long start = System.nanoTime();
                recommender.compute(squad, request);
                if (i >= WARMUP) {
                    millis.add((System.nanoTime() - start) / 1_000_000);
                }
            }
        }
        long[] sorted = millis.stream().mapToLong(Long::longValue).sorted().toArray();
        md.append(String.format(Locale.ROOT, "## Süre (hat içi, HTTP hariç; %d ölçüm, ısınma %d tur)\n\np50 %d ms · p95 %d ms · en yavaş %d ms · ortalama recall@10 %.3f\n", sorted.length, WARMUP,
                sorted[sorted.length / 2], sorted[(int) Math.floor(sorted.length * 0.95)], sorted[sorted.length - 1], recalls.stream().mapToDouble(Double::doubleValue).average().orElse(0)));
        Files.writeString(Path.of(output), md.toString(), StandardCharsets.UTF_8);
        log.info("Career recommendation report written path : {}", output);
    }

    private static String table(String position, CareerRecommendationResponse response) {
        StringBuilder sb = new StringBuilder("## ").append(position).append("\n\n");
        if (response.getCurrent() != null) {
            sb.append(String.format(Locale.ROOT, "Mevcut: **%s** (currentFit %.1f, rol %s)\n\n", response.getCurrent().getPlayerName(), response.getCurrent().getCurrentFit(), response.getCurrent().getRoleId()));
        } else {
            sb.append("Mevcut: kadroda bu mevkiyi oynayabilen oyuncu yok\n\n");
        }
        sb.append("| # | Oyuncu | Yaş | Kulüp (lig) | Değer | Kova (puan) | Rol | currentFit | levelFit | Δ mevcut | futureFit | Etiket | En büyük artılar | Eksiler | Kova gerekçesi |\n|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        int n = 1;
        for (CareerRecommendationResponse.Item item : response.getItems()) {
            sb.append(String.format(Locale.ROOT, "| %d | %s | %s | %s (%s) | %s | %s (%d) | %s | %.1f | %s | %s | %s | %s | %s | %s | %s |%n", n++, item.getPlayer().getName(),
                    item.getPlayer().getAge() == null ? "?" : item.getPlayer().getAge(), nz(item.getPlayer().getClub()), nz(item.getPlayer().getLeague()),
                    item.getPlayer().getValueEur() == null ? "—" : String.format(Locale.ROOT, "%.1f M€", item.getPlayer().getValueEur() / 1_000_000.0),
                    item.getBucketLabel(), item.getBucketPoints(), item.getRoleName() + " " + item.getFocus(), item.getCurrentFit(),
                    item.getLevelFit() == null ? "—" : String.format(Locale.ROOT, "%.1f", item.getLevelFit()),
                    item.getDeltaVsCurrent() == null ? "—" : String.format(Locale.ROOT, "%+.1f", item.getDeltaVsCurrent()),
                    item.getFutureFit() == null ? "—" : String.format(Locale.ROOT, "%.1f", item.getFutureFit()), String.join(",", item.getTags()),
                    contributions(item.getPros()), contributions(item.getCons()),
                    item.getBucketFactors().stream().map((f) -> f.name() + " " + f.value() + " (" + f.points() + ")").collect(Collectors.joining("; "))));
        }
        return sb.toString();
    }

    private static String contributions(List<com.fcareer.fitengine.service.FitEngineV3.Contribution> list) {
        return list.stream().map((c) -> c.key() + " " + String.format(Locale.ROOT, "%+.1f", c.points())).collect(Collectors.joining(", "));
    }

    private static String nz(String value) {
        return value == null ? "—" : value;
    }

}
