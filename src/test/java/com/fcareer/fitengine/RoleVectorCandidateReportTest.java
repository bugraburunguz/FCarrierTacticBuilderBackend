package com.fcareer.fitengine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fcareer.fitengine.norms.AttrNorms;
import com.fcareer.fitengine.norms.PosDist;
import com.fcareer.fitengine.norms.RoleSeeds;
import com.fcareer.fitengine.service.FitEngineV3;
import com.fcareer.fitengine.RoleFamiliarityValidationTest.Entry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * E3-4b aday ölçümü (gerçek motor + levelFit): learn-role-vectors.mjs'in yazdığı aday vektör dosyalarını (-Drole.candidates=<klasör>) mevcut seed'le
 * oyuncu içi AUC (eğitim/test yarısı ayrı), altın set kesin çiftleri ve en çok değişen oyuncular açısından karşılaştırır. Seed'e YAZMAZ.
 * Çıktı: <klasör>/report.json. PlayStyle terimi -Drole.validation.ps=true ile açılır; aşinalık (FAM) her zaman kapalı.
 */
@EnabledIfSystemProperty(named = "role.candidates", matches = ".+")
class RoleVectorCandidateReportTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AttrNorms NORMS = new AttrNorms(MAPPER);
    private static final PosDist POS_DIST = new PosDist(MAPPER);
    private static final int TOP_CHANGED = 3;
    private static final String ALL = "all";

    private record Pairs(double wins, double count) {
    }

    @Test
    void writesTheCandidateReport() throws Exception {
        Path dir = Path.of(System.getProperty("role.candidates"));
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
        List<Entry> entries = RoleFamiliarityValidationTest.load(codes, roleIdByName);
        Map<Entry, Integer> index = new IdentityHashMap<>();
        Map<String, List<Entry>> pools = new HashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            index.put(entries.get(i), i);
            for (String p : entries.get(i).player().getPositions()) {
                pools.computeIfAbsent(entries.get(i).gender() + "|" + p, (k) -> new ArrayList<>()).add(entries.get(i));
            }
        }
        RoleSeeds baselineSeeds = new RoleSeeds(MAPPER);
        FitEngineV3 baselineEngine = new FitEngineV3(NORMS, POS_DIST, baselineSeeds);
        Map<String, double[]> baseline = new TreeMap<>();
        for (JsonNode role : roles) {
            computeRole(baselineEngine, role, pools, index, entries.size(), baseline);
        }

        ObjectNode report = MAPPER.createObjectNode();
        report.set("baseline", summarize(baseline, entries, roles, null));
        ObjectNode perRole = report.putObject("roles");
        List<Path> candidates;
        try (var files = Files.list(dir)) {
            candidates = files.filter((p) -> p.getFileName().toString().startsWith("candidate-")).sorted().toList();
        }
        for (Path candidate : candidates) {
            String id = candidate.getFileName().toString().replace("candidate-", "").replace(".json", "");
            RoleSeeds seeds = RoleSeeds.withVectors(MAPPER, candidate);
            FitEngineV3 engine = new FitEngineV3(NORMS, POS_DIST, seeds);
            Map<String, double[]> levels = new TreeMap<>(baseline);
            List<JsonNode> changed = ALL.equals(id) ? roles : roles.stream().filter((r) -> r.path("id").asText().equals(id)).toList();
            changed.forEach((r) -> computeRole(engine, r, pools, index, entries.size(), levels));
            ObjectNode node = (ObjectNode) summarize(levels, entries, roles, id);
            GoldenSupport.Result golden = GoldenSupport.evaluate(engine, MAPPER, ALL.equals(id) ? null : id);
            node.put("goldenStrictTotal", golden.strictTotal());
            ArrayNode failures = node.putArray("goldenStrictFailures");
            golden.strictFailures().forEach(failures::add);
            ArrayNode calibration = node.putArray("goldenCalibrationFailures");
            golden.calibrationFailures().forEach(calibration::add);
            node.set("top", topChanged(baseline, levels, entries, id, roles));
            perRole.set(id, node);
        }
        Files.writeString(dir.resolve("report.json"), MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(report));
    }

    /** Rolün her (mevki, focus) vektörü için oyuncu başına levelFit; focus'lar arasında en yüksek değer alınır. */
    private static void computeRole(FitEngineV3 engine, JsonNode role, Map<String, List<Entry>> pools, Map<Entry, Integer> index, int size, Map<String, double[]> out) {
        String roleId = role.path("id").asText();
        for (JsonNode position : role.path("positions")) {
            double[] best = new double[size];
            Arrays.fill(best, Double.NaN);
            for (JsonNode focus : role.path("focuses")) {
                for (int gender = 0; gender <= 1; gender++) {
                    List<Entry> pool = pools.getOrDefault(gender + "|" + position.asText(), List.of());
                    if (pool.isEmpty()) {
                        continue;
                    }
                    double[] levels = RoleFamiliarityValidationTest.levelFits(engine, pool, position.asText(), roleId, focus.asText());
                    for (int i = 0; i < pool.size(); i++) {
                        int at = index.get(pool.get(i));
                        best[at] = Double.isNaN(best[at]) ? levels[i] : Math.max(best[at], levels[i]);
                    }
                }
            }
            out.put(position.asText() + "|" + roleId, best);
        }
    }

    /** Oyuncu içi AUC: global ve anahtar (mevki|rol) bazında, eğitim (0) ve test (1) yarısı ayrı. `focusRole` verilirse o rolün anahtarları da ayrıca özetlenir. */
    private static JsonNode summarize(Map<String, double[]> levels, List<Entry> entries, List<JsonNode> roles, String focusRole) {
        Map<String, List<String>> keysByPosition = new HashMap<>();
        for (JsonNode role : roles) {
            for (JsonNode position : role.path("positions")) {
                keysByPosition.computeIfAbsent(position.asText(), (k) -> new ArrayList<>()).add(position.asText() + "|" + role.path("id").asText());
            }
        }
        Map<String, Pairs>[] perKey = new Map[2];
        double[][] global = new double[2][2];
        for (int h = 0; h < 2; h++) {
            perKey[h] = new TreeMap<>();
        }
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            int h = RoleSplit.half(entry.player().getId());
            for (String position : entry.player().getPositions()) {
                List<String> keys = keysByPosition.getOrDefault(position, List.of());
                for (String positive : keys) {
                    if (!entry.roles().containsKey(positive) || Double.isNaN(levels.get(positive)[i])) {
                        continue;
                    }
                    for (String negative : keys) {
                        if (entry.roles().containsKey(negative) || Double.isNaN(levels.get(negative)[i])) {
                            continue;
                        }
                        double p = levels.get(positive)[i];
                        double n = levels.get(negative)[i];
                        double score = p > n ? 1 : p == n ? 0.5 : 0;
                        Pairs previous = perKey[h].getOrDefault(positive, new Pairs(0, 0));
                        perKey[h].put(positive, new Pairs(previous.wins() + score, previous.count() + 1));
                        global[h][0] += score;
                        global[h][1] += 1;
                    }
                }
            }
        }
        ObjectNode node = MAPPER.createObjectNode();
        node.put("trainGlobal", global[0][0] / global[0][1]).put("testGlobal", global[1][0] / global[1][1]);
        ObjectNode keys = node.putObject("keys");
        for (String key : perKey[1].keySet()) {
            ObjectNode k = keys.putObject(key);
            Pairs train = perKey[0].getOrDefault(key, new Pairs(0, 0));
            Pairs test = perKey[1].get(key);
            k.put("train", train.count() == 0 ? Double.NaN : train.wins() / train.count()).put("test", test.wins() / test.count()).put("testPairs", test.count());
        }
        return node;
    }

    private static JsonNode topChanged(Map<String, double[]> before, Map<String, double[]> after, List<Entry> entries, String id, List<JsonNode> roles) {
        ArrayNode out = MAPPER.createArrayNode();
        record Change(long player, String key, double before, double after) {
        }
        List<Change> changes = new ArrayList<>();
        for (Map.Entry<String, double[]> e : after.entrySet()) {
            if (!ALL.equals(id) && !e.getKey().endsWith("|" + id)) {
                continue;
            }
            double[] b = before.get(e.getKey());
            for (int i = 0; i < entries.size(); i++) {
                if (RoleSplit.half(entries.get(i).player().getId()) == 1 && !Double.isNaN(e.getValue()[i]) && !Double.isNaN(b[i]) && entries.get(i).player().getOverall() >= 55) {
                    changes.add(new Change(entries.get(i).player().getId(), e.getKey(), b[i], e.getValue()[i]));
                }
            }
        }
        changes.stream().sorted((a, b) -> Double.compare(Math.abs(b.after() - b.before()), Math.abs(a.after() - a.before()))).limit(TOP_CHANGED)
                .forEach((c) -> out.addObject().put("playerId", c.player()).put("key", c.key()).put("before", c.before()).put("after", c.after()));
        return out;
    }
}
