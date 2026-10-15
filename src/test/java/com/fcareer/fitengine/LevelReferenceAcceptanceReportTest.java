package com.fcareer.fitengine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.fitengine.RoleFamiliarityValidationTest.Entry;
import com.fcareer.fitengine.level.LevelFitCalculator;
import com.fcareer.fitengine.model.AcceleRate;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.norms.AttrNorms;
import com.fcareer.fitengine.norms.PosDist;
import com.fcareer.fitengine.norms.RoleSeeds;
import com.fcareer.fitengine.service.FitEngineV3;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * V3-06c/0a: v3 kabul ölçütleri (13 §3.5). Ürün referansı her yerde oyuncunun kendi OVR bandı (kariyer = katalog; V3-06c kararı). Lig bandı satırları
 * (sabit lig gücü; League Two = 62, ayrıca 55..85 taraması) yalnız bilgi amaçlıdır, ürün kullanmaz. Ölçütler: altın set kesin çiftler %100 (currentFit ve levelFit),
 * Role++ vs rolsüz AUC ≥ 0,80, oyuncu içi AUC ≥ 0,60. Yalnız rapor (target/level-reference-acceptance.md); eşik burada kırılmaz
 * (regresyon RoleSignalRegressionTest'te). Opt-in: -Dacceptance.report=true; LE dökümü yoksa atlanır.
 */
class LevelReferenceAcceptanceReportTest {

    private static final Path DUMP = Path.of("ml-service", "data", "fc27-dump", "players.csv");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MIN_POSITIVES = 15;
    private static final int MIN_BAND = 100;
    private static final int HALF = 3;
    private static final int MAX_HALF = 15;
    /** Kariyer referansları: League Two (Crewe, Port Vale; kayıttan 62) + tarama. */
    private static final int[] LEAGUE_OVRS = {55, 62, 66, 70, 74, 78, 82, 85};

    private record Result(String label, int goldenOk, int goldenTotal, double rolePlusPlus, double withinPlayer) {
    }

    @Test
    void writesTheAcceptanceReportForBothReferences() throws Exception {
        assumeTrue(Boolean.getBoolean("acceptance.report") && Files.exists(DUMP), "opt-in -Dacceptance.report=true ve LE dökümü gerekli");
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
        JsonNode golden;
        try (InputStream in = new ClassPathResource("golden-set.fc27.json").getInputStream()) {
            golden = MAPPER.readTree(in);
        }

        Map<String, IntUnaryOperator> references = new LinkedHashMap<>();
        references.put("kariyer ve katalog (kendi OVR bandı)", IntUnaryOperator.identity());
        for (int ovr : LEAGUE_OVRS) {
            references.put("lig bandı OVR " + ovr + (ovr == 62 ? " (League Two: Crewe, Port Vale)" : ""), (o) -> ovr);
        }
        int[] currentFit = golden(engine, golden, pools, null);
        List<Result> results = new ArrayList<>();
        for (Map.Entry<String, IntUnaryOperator> ref : references.entrySet()) {
            Map<String, double[]> levels = new TreeMap<>();
            roles.forEach((r) -> RoleVectorCandidateReportTest.computeRole(engine, r, pools, index, entries.size(), levels, ref.getValue()));
            int[] g = golden(engine, golden, pools, ref.getValue());
            results.add(new Result(ref.getKey(), g[0], g[1], rolePlusPlusAuc(levels, entries), withinPlayerAuc(levels, entries, roles)));
            System.out.println("ACCEPTANCE " + ref.getKey() + " golden " + g[0] + "/" + g[1] + " role++ " + results.get(results.size() - 1).rolePlusPlus()
                    + " within " + results.get(results.size() - 1).withinPlayer());
        }
        StringBuilder md = new StringBuilder();
        md.append(String.format("Altın set kesin çiftler (ölçüt, currentFit; referanstan bağımsız): %d/%d%s%n%n", currentFit[0], currentFit[1],
                currentFit[0] == currentFit[1] ? "" : " **altında**"));
        String header = "| Referans | Altın set kesin çiftler, levelFit ile (bilgi) | Role++ vs rolsüz AUC (≥ 0,80) | Oyuncu içi AUC (≥ 0,60) |\n|---|---|---|---|\n";
        md.append("### Ürün referansı (kariyer ve katalog aynı: oyuncunun kendi OVR bandı)\n\n").append(header);
        for (Result r : results) {
            if (r == results.get(1)) {
                md.append("\n### Lig bandı — bilgi amaçlı (ürün kullanmıyor; V3-06c/0a kararı)\n\n").append(header);
            }
            md.append(String.format("| %s | %d/%d | %.3f%s | %.3f%s |%n", r.label(), r.goldenOk(), r.goldenTotal(),
                    r.rolePlusPlus(), r.rolePlusPlus() >= 0.80 ? "" : " **altında**", r.withinPlayer(), r.withinPlayer() >= 0.60 ? "" : " **altında**"));
        }
        Path out = Path.of("target", "level-reference-acceptance.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, md.toString(), StandardCharsets.UTF_8);
    }

    /** RoleSignalRegressionTest ile aynı tanım: rol bazında Role++ sayısıyla ağırlıklı ++ vs rolsüz AUC. */
    private static double rolePlusPlusAuc(Map<String, double[]> levels, List<Entry> entries) {
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
        return weighted / weight;
    }

    private static double withinPlayerAuc(Map<String, double[]> levels, List<Entry> entries, List<JsonNode> roles) {
        JsonNode within = RoleVectorCandidateReportTest.summarize(levels, entries, roles, null);
        return (within.path("trainGlobal").asDouble() + within.path("testGlobal").asDouble()) / 2;
    }

    /** Kesin çiftler: bandOvr null ise currentFit; değilse levelFit (bant LE dökümünün aynı mevki havuzundan, erkek). [doğru, toplam]. */
    private static int[] golden(FitEngineV3 engine, JsonNode golden, Map<String, List<Entry>> pools, IntUnaryOperator bandOvr) {
        Map<String, FitPlayer> players = new HashMap<>();
        golden.path("players").fields().forEachRemaining((e) -> players.put(e.getKey(), toPlayer(e.getValue())));
        Map<String, double[]> poolFits = new HashMap<>();
        int ok = 0;
        int total = 0;
        for (JsonNode block : golden.path("blocks")) {
            List<String> blockPositions = new ArrayList<>();
            block.path("positions").forEach((p) -> blockPositions.add(p.asText()));
            for (JsonNode pair : block.path("strict")) {
                double high = score(engine, block, blockPositions, players.get(pair.get(0).asText()), pools, poolFits, bandOvr);
                double low = score(engine, block, blockPositions, players.get(pair.get(1).asText()), pools, poolFits, bandOvr);
                total++;
                ok += high > low ? 1 : 0;
            }
        }
        return new int[] {ok, total};
    }

    private static double score(FitEngineV3 engine, JsonNode block, List<String> blockPositions, FitPlayer player, Map<String, List<Entry>> pools,
                                Map<String, double[]> poolFits, IntUnaryOperator bandOvr) {
        String position = player.getPositions().stream().filter(blockPositions::contains).findFirst().orElse(blockPositions.get(0));
        String role = block.path("role").asText();
        String focus = block.path("focus").asText();
        double fit = engine.currentFit(player, position, role, focus, Set.of()).currentFit();
        if (bandOvr == null) {
            return fit;
        }
        List<Entry> pool = pools.getOrDefault("0|" + position, List.of());
        double[] fits = poolFits.computeIfAbsent(position + "|" + role + "|" + focus,
                (k) -> pool.stream().mapToDouble((e) -> engine.currentFit(e.player(), position, role, focus, Set.of()).currentFit()).toArray());
        int[] ovrs = pool.stream().mapToInt((e) -> e.player().getOverall()).toArray();
        int[] sorted = Arrays.stream(ovrs).sorted().toArray();
        LevelFitCalculator.Band band = LevelFitCalculator.band(sorted, bandOvr.applyAsInt(player.getOverall()), MIN_BAND, HALF, MAX_HALF);
        List<Double> inBand = new ArrayList<>();
        for (int k = 0; k < fits.length; k++) {
            if (ovrs[k] >= band.lo() && ovrs[k] <= band.hi()) {
                inBand.add(fits[k]);
            }
        }
        return LevelFitCalculator.smooth(LevelFitCalculator.quantiles(inBand.stream().mapToDouble(Double::doubleValue).toArray()), fit);
    }

    private static FitPlayer toPlayer(JsonNode n) {
        Map<Attr, Integer> attrs = new EnumMap<>(Attr.class);
        n.path("attrs").fields().forEachRemaining((e) -> Attr.fromKey(e.getKey()).ifPresent((a) -> attrs.put(a, e.getValue().asInt())));
        Map<String, Integer> playstyles = new HashMap<>();
        n.path("playstyles").fields().forEachRemaining((e) -> playstyles.put(e.getKey(), e.getValue().asInt()));
        List<String> positions = new ArrayList<>();
        n.path("positions").forEach((p) -> positions.add(p.asText()));
        return FitPlayer.builder().id(n.path("id").asLong()).name(n.path("name").asText()).overall(n.path("ovr").asInt()).positions(positions)
                .attrs(attrs).playstyles(playstyles).acceleRate(AcceleRate.fromLabel(n.path("acceleRate").asText(null)).orElse(null))
                .gender(0).preferredFoot(n.path("foot").isNull() ? null : n.path("foot").asText()).weakFoot(n.path("weakFoot").isNull() ? null : n.path("weakFoot").asInt())
                .skillMoves(n.path("skillMoves").isNull() ? null : n.path("skillMoves").asInt()).heightCm(n.path("heightCm").isNull() ? null : n.path("heightCm").asInt())
                .roleFamiliarity(Map.of()).build();
    }
}
