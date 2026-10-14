package com.fcareer.fitengine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.fitengine.RoleFamiliarityValidationTest.Entry;
import com.fcareer.fitengine.norms.AttrNorms;
import com.fcareer.fitengine.norms.PosDist;
import com.fcareer.fitengine.norms.RoleSeeds;
import com.fcareer.fitengine.service.FitEngineV3;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * EA Role+/++ etiketine karşı regresyon (Buğra kararı, 13 §3.5): motor/seed değişikliği bu iki ölçütü kötüleştirirse build kırılır.
 *   - Role++ vs rolsüz AUC (rol bazında pozitif sayısıyla ağırlıklı) ≥ 0,80
 *   - oyuncu içi AUC (aynı oyuncunun aynı mevkideki Role+/++ rolleri vs rolsüzleri) ≥ 0,60  — izleme metriği
 * Etiket rol tanımının değil EA atama kuralının vekilidir; bu yüzden eşikler "iyileştirme hedefi" değil taban çizgisidir (ölçülen: ≈0,84 ve ≈0,63).
 * PlayStyle terimi AÇIK, aşinalık (FAM) KAPALI. Veri: yerel LE dökümü (git dışı); dosya yoksa test atlanır.
 */
class RoleSignalRegressionTest {

    private static final double MIN_ROLE_PLUS_PLUS_AUC = 0.80;
    private static final double MIN_WITHIN_PLAYER_AUC = 0.60;
    private static final int MIN_POSITIVES = 15;
    private static final Path DUMP = Path.of("ml-service", "data", "fc27-dump", "players.csv");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void rolePlusPlusAndWithinPlayerSignalsStayAboveTheirFloors() throws Exception {
        assumeTrue(Files.exists(DUMP), "LE dökümü yok (ml-service/data/fc27-dump/players.csv); regresyon testi atlandı");
        JsonNode codes;
        Map<String, String> roleIdByName = new HashMap<>();
        List<JsonNode> roles = new ArrayList<>();
        try (InputStream in = new ClassPathResource("seed/le-role-codes.json").getInputStream()) {
            codes = MAPPER.readTree(in).path("codes");
        }
        try (InputStream in = new ClassPathResource("seed/roles.json").getInputStream()) {
            MAPPER.readTree(in).forEach((r) -> {
                roleIdByName.put(RoleFamiliarityValidationTest.normalize(r.path("name").asText()), r.path("id").asText());
                roles.add(r);
            });
        }
        boolean previous = RoleFamiliarityValidationTest.PLAYSTYLES_ON;
        List<Entry> entries;
        try {
            RoleFamiliarityValidationTest.PLAYSTYLES_ON = true;
            entries = RoleFamiliarityValidationTest.load(codes, roleIdByName);
        } finally {
            RoleFamiliarityValidationTest.PLAYSTYLES_ON = previous;
        }
        Map<Entry, Integer> index = new IdentityHashMap<>();
        Map<String, List<Entry>> pools = new HashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            index.put(entries.get(i), i);
            for (String p : entries.get(i).player().getPositions()) {
                pools.computeIfAbsent(entries.get(i).gender() + "|" + p, (k) -> new ArrayList<>()).add(entries.get(i));
            }
        }
        FitEngineV3 engine = new FitEngineV3(new AttrNorms(MAPPER), new PosDist(MAPPER), new RoleSeeds(MAPPER));
        Map<String, double[]> levels = new TreeMap<>();
        roles.forEach((r) -> RoleVectorCandidateReportTest.computeRole(engine, r, pools, index, entries.size(), levels));

        double weighted = 0;
        double weight = 0;
        for (Map.Entry<String, double[]> e : levels.entrySet()) {
            List<Double> plusPlus = new ArrayList<>();
            List<Double> none = new ArrayList<>();
            for (int i = 0; i < entries.size(); i++) {
                double level = e.getValue()[i];
                if (Double.isNaN(level)) {
                    continue;
                }
                Integer familiarity = entries.get(i).roles().get(e.getKey());
                if (familiarity == null) {
                    none.add(level);
                } else if (familiarity >= 2) {
                    plusPlus.add(level);
                }
            }
            if (plusPlus.size() >= MIN_POSITIVES / 3 && none.size() >= MIN_POSITIVES) {
                weighted += RoleFamiliarityValidationTest.auc(plusPlus, none) * plusPlus.size();
                weight += plusPlus.size();
            }
        }
        double rolePlusPlusAuc = weighted / weight;

        JsonNode within = RoleVectorCandidateReportTest.summarize(levels, entries, roles, null);
        double withinPlayerAuc = (within.path("trainGlobal").asDouble() + within.path("testGlobal").asDouble()) / 2;
        System.out.println("ROLE-SIGNAL Role++ vs rolsüz AUC=" + String.format("%.3f", rolePlusPlusAuc) + " oyuncu içi AUC=" + String.format("%.3f", withinPlayerAuc));
        assertThat(rolePlusPlusAuc).as("Role++ vs rolsüz AUC (ağırlıklı)").isGreaterThanOrEqualTo(MIN_ROLE_PLUS_PLUS_AUC);
        assertThat(withinPlayerAuc).as("oyuncu içi AUC").isGreaterThanOrEqualTo(MIN_WITHIN_PLAYER_AUC);
    }
}
