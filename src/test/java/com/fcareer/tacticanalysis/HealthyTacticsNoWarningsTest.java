package com.fcareer.tacticanalysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.tacticanalysis.TacticAnalysisModels.Analysis;
import com.fcareer.tacticanalysis.TacticAnalysisModels.Finding;
import com.fcareer.tacticanalysis.TacticAnalysisModels.Input;
import com.fcareer.tacticanalysis.TacticAnalysisModels.Severity;
import com.fcareer.tacticanalysis.TacticAnalysisModels.Slot;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E3-7b negatif set: popüler meta formasyonlar, formations.json varsayılan rolleri, Balanced, hat 50, yalnız rollerle analiz → 0 "uyarı" ve 0 "risk" (bilgi serbest).
 * Sağlıklı bir taktiğe uyarı basan kural kalibrasyon hatasıdır (kural ya da eşik düzeltilir, taktik değil).
 */
class HealthyTacticsNoWarningsTest {

    private static final List<String> META = List.of("4-3-3", "4-2-3-1", "4-2-1-3", "3-5-2", "4-1-2-1-2", "4-4-2", "4-3-2-1", "5-2-1-2");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RoleMovements movements = new RoleMovements(MAPPER);
    private final TacticRules rules = new TacticRules(MAPPER);
    private final MetricsCalculator calculator = new MetricsCalculator();
    private final TacticAnalyzer analyzer = new TacticAnalyzer(movements, rules, calculator, new RuleEngine(rules, new RuleMetrics(calculator)));

    private List<Slot> slotsOf(String formationId) throws Exception {
        try (InputStream in = new ClassPathResource("seed/formations.json").getInputStream()) {
            for (JsonNode f : MAPPER.readTree(in)) {
                if (f.path("id").asText().equals(formationId)) {
                    List<Slot> slots = new ArrayList<>();
                    for (JsonNode s : f.path("slots")) {
                        slots.add(new Slot(s.path("slotId").asText(), s.path("position").asText(), s.path("x").asInt(), s.path("y").asInt(), s.path("defaultRole").asText()));
                    }
                    return slots;
                }
            }
        }
        throw new AssertionError("formasyon yok: " + formationId);
    }

    @Test
    void popularFormationsWithDefaultRolesProduceNeitherWarningsNorRisks() throws Exception {
        Map<String, List<String>> offenders = new java.util.LinkedHashMap<>();
        for (String id : META) {
            Analysis a = analyzer.analyze(new Input(id, BuildUpType.BALANCED, 50, slotsOf(id), Map.of()));
            List<String> bad = a.findings().stream().filter((f) -> f.severity() != Severity.INFO).map(Finding::code).toList();
            if (!bad.isEmpty()) {
                offenders.put(id, bad);
            }
        }
        assertThat(offenders).as("uyarı/risk üreten sağlıklı formasyonlar").isEmpty();
    }
}
