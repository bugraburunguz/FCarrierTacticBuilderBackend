package com.fcareer.tacticanalysis;

import com.fcareer.catalog.service.PlayerService;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.tacticanalysis.TacticAnalysisModels.Analysis;
import com.fcareer.tacticanalysis.TacticAnalysisModels.Finding;
import com.fcareer.tacticanalysis.TacticAnalysisModels.Input;
import com.fcareer.tacticanalysis.TacticAnalysisModels.Slot;
import com.fcareer.tactics.model.FormationDefinition;
import com.fcareer.tactics.model.FormationSlot;
import com.fcareer.tactics.service.FormationRegistry;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Yerel rapor (E3-7b): aynı taktiğin iki bağlamda (dev kulüp ve alt lig takımı) bulguları. Takımın oyuncuları mevkiye göre OVR sırasıyla yerleştirilir; seviye referansı takımın kendi ligindendir.
 * `fcareer.report.tactic.enabled=true` ve `.career-id=<id>` ile açılışta yazar; varsayılan kapalı, yalnız yerel veritabanında elle çalıştırılır.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "fcareer.report.tactic.enabled", havingValue = "true")
public class TacticCalibrationReportRunner implements ApplicationRunner {

    private record Team(String label, String name, String league) {
    }

    private record Tactic(String id, String formation, BuildUpType buildUp, int depth, Map<String, String> roles) {
    }

    private static final List<Team> TEAMS = List.of(new Team("Dev kulüp", "Real Madrid", "LALIGA EA SPORTS"), new Team("Alt lig", "Crewe Alexandra", "EFL League Two"));
    private static final List<Tactic> TACTICS = List.of(
            new Tactic("A", "4-2-3-1 (2)", BuildUpType.BALANCED, 55, Map.of("LB", "fullback_defend", "RB", "attacking_wingback_attack", "LDM", "holding_defend", "RDM", "holding_defend", "LM", "inside_forward_attack",
                    "CAM", "playmaker_balanced", "RM", "winger_balanced", "ST", "advanced_forward_attack")),
            new Tactic("C", "4-3-3", BuildUpType.COUNTER, 80, Map.of("LB", "attacking_wingback_attack", "RB", "attacking_wingback_attack", "LW", "inside_forward_attack", "RW", "inside_forward_attack")),
            new Tactic("E", "4-2-3-1 (2)", BuildUpType.SHORT, 95, Map.of("LB", "attacking_wingback_attack", "RB", "attacking_wingback_attack", "LDM", "box_crasher_balanced", "RDM", "box_crasher_balanced",
                    "LM", "winger_attack", "RM", "winger_attack", "CAM", "classic_10_attack", "ST", "false_9_build_up")));

    private final TacticAnalyzer analyzer;
    private final RoleMovements movements;
    private final FormationRegistry formations;
    private final PlayerService playerService;
    private final LevelReferenceService levelReferences;
    private final JdbcTemplate jdbc;

    @org.springframework.beans.factory.annotation.Value("${fcareer.report.tactic.career-id}")
    private long careerId;
    @org.springframework.beans.factory.annotation.Value("${fcareer.report.tactic.output:/tmp/e3-7b-iki-baglam.md}")
    private String output;

    @Override
    public void run(ApplicationArguments args) throws IOException {
        Short gameVersion = jdbc.queryForObject("SELECT game_version_id FROM career WHERE id = ?", Short.class, careerId);
        StringBuilder md = new StringBuilder("# İki bağlam: aynı taktik, dev kulüp ve alt lig\n\nTakımın oyuncuları mevkiye göre OVR sırasıyla yerleştirildi; seviye referansı takımın kendi liginin aynı mevki medyanıdır (kanıtta değer · referans · marj).\n\n");
        for (Tactic t : TACTICS) {
            md.append("## Taktik ").append(t.id()).append(" — ").append(t.formation()).append(" · ").append(t.buildUp()).append(" · hat ").append(t.depth()).append("\n\n");
            FormationDefinition formation = formations.get(t.formation());
            for (Team team : TEAMS) {
                Integer teamId = jdbc.queryForObject("SELECT team_id FROM career_team WHERE career_id = ? AND name = ? AND league_name = ?", Integer.class, careerId, team.name(), team.league());
                List<Long> owned = jdbc.queryForList("SELECT player_id FROM career_player WHERE career_id = ? AND live AND team_id = ?", Long.class, careerId, teamId);
                Map<String, FitPlayer> assigned = assign(formation, owned, gameVersion);
                List<Slot> slots = new ArrayList<>();
                for (FormationSlot s : formation.slots()) {
                    slots.add(new Slot(s.slotId(), s.position(), s.x(), s.y(), t.roles().getOrDefault(s.slotId(), s.defaultRole())));
                }
                LevelReferenceService.LevelRefs level = levelReferences.forCareer(careerId, owned);
                Analysis a = analyzer.analyze(new Input(formation.id(), t.buildUp(), t.depth(), slots, assigned, level.refs(), level.label()));
                md.append("### ").append(team.label()).append(" — ").append(team.name()).append(" (").append(team.league()).append(")\n\nReferans: ").append(level.refs().isEmpty() ? "yok" : level.refs().toString()).append("\n\n");
                md.append(findings(a));
            }
        }
        Files.writeString(Path.of(output), md.toString(), StandardCharsets.UTF_8);
        log.info("Tactic calibration report written path : {}", output);
    }

    private Map<String, FitPlayer> assign(FormationDefinition formation, List<Long> owned, Short gameVersion) {
        Map<Long, FitPlayer> players = new HashMap<>();
        playerService.fitPlayers(owned, gameVersion, careerId).forEach((p) -> players.put(p.getId(), p));
        List<FitPlayer> byOvr = players.values().stream().sorted(Comparator.comparingInt((FitPlayer p) -> p.getOverall() == null ? 0 : p.getOverall()).reversed()).toList();
        Set<Long> used = new HashSet<>();
        Map<String, FitPlayer> out = new LinkedHashMap<>();
        for (FormationSlot s : formation.slots()) {
            byOvr.stream().filter((p) -> !used.contains(p.getId()) && p.getPositions().contains(s.position())).findFirst().or(() -> byOvr.stream().filter((p) -> !used.contains(p.getId())).findFirst())
                    .ifPresent((p) -> {
                        used.add(p.getId());
                        out.put(s.slotId(), p);
                    });
        }
        return out;
    }

    private static String findings(Analysis a) {
        if (a.findings().isEmpty()) {
            return "_Bulgu yok._\n\n";
        }
        StringBuilder sb = new StringBuilder("| Kod | Şiddet | Kanıt (değer işlem eşik · referans · marj) | Kullanıcı metni |\n|---|---|---|---|\n");
        for (Finding f : a.findings()) {
            String ev = f.evidence().stream().map((e) -> e.metric() + " = " + n(e.value()) + " " + e.op() + " " + n(e.threshold()) + (e.reference() == null ? "" : " · referans " + n(e.reference()) + " · marj " + n(e.margin())))
                    .reduce((x, y) -> x + "<br>" + y).orElse("");
            sb.append("| ").append(f.code()).append(f.instance().isEmpty() ? "" : " [" + f.instance().get("side") + "]").append(" | ").append(f.severity()).append(" | ").append(ev).append(" | ").append(f.text()).append(" |\n");
        }
        return sb.append("\n").toString();
    }

    private static String n(double v) {
        return String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
