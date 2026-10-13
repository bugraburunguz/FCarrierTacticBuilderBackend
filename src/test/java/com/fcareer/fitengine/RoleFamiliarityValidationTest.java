package com.fcareer.fitengine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.fitengine.level.LevelFitCalculator;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.norms.AttrNorms;
import com.fcareer.fitengine.norms.PosDist;
import com.fcareer.fitengine.norms.RoleSeeds;
import com.fcareer.fitengine.service.FitEngineV3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * E3-5b bağımsız doğrulama (golden set'ten bağımsız tek test): EA'nın oyuncuya verdiği Role+ / Role++ bilgisi (LE players.csv role1..9)
 * ile bizim levelFit'imiz uyuşuyor mu? Aşinalık (FAM) terimi KAPALI (roleFamiliarity boş), PlayStyle terimi de kapalı (trait bitmask çözülmedi).
 * Ağırlıklar bu sonuca göre AYARLANMAZ; yalnız rapor üretilir (target/role-validation.md). Opt-in: -Drole.validation=true
 */
@EnabledIfSystemProperty(named = "role.validation", matches = "true")
class RoleFamiliarityValidationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final RoleSeeds SEEDS = new RoleSeeds(MAPPER);
    private static final FitEngineV3 ENGINE = new FitEngineV3(new AttrNorms(MAPPER), new PosDist(MAPPER), SEEDS);
    private static final Path DUMP = Path.of("ml-service", "data", "fc27-dump", "players.csv");
    private static final Map<Integer, String> POSITION_CODES = Map.ofEntries(Map.entry(0, "GK"), Map.entry(3, "RB"), Map.entry(5, "CB"), Map.entry(7, "LB"),
            Map.entry(10, "CDM"), Map.entry(12, "RM"), Map.entry(14, "CM"), Map.entry(16, "LM"), Map.entry(18, "CAM"), Map.entry(23, "RW"), Map.entry(25, "ST"), Map.entry(27, "LW"));
    private static final Map<Attr, String> COLUMNS = columns();
    private static final int PLUS_PLUS = 100;
    private static final double WEAK_AUC = 0.6;
    private static final double TARGET_AUC = 0.75;
    private static final int MIN_GROUP = 15;
    private static final int MIN_BAND = 100;
    private static final int HALF = 3;
    private static final int MAX_HALF = 15;

    private record Entry(FitPlayer player, int gender, Map<String, Integer> roles) {
    }

    private record Group(String position, String role, List<Double> plusPlus, List<Double> plus, List<Double> none) {
    }

    private static Map<Attr, String> columns() {
        Map<Attr, String> m = new EnumMap<>(Attr.class);
        for (Attr a : Attr.values()) {
            m.put(a, switch (a) {
                case FK_ACCURACY -> "freekickaccuracy";
                case HEADING -> "headingaccuracy";
                case DEF_AWARENESS -> "defensiveawareness";
                default -> a.getKey().toLowerCase(java.util.Locale.ROOT);
            });
        }
        return m;
    }

    private static String normalize(String s) {
        return s.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    @Test
    void writesTheIndependentValidationReport() throws Exception {
        JsonNode codes;
        try (InputStream in = new ClassPathResource("seed/le-role-codes.json").getInputStream()) {
            codes = MAPPER.readTree(in).path("codes");
        }
        Map<String, String> roleIdByName = new HashMap<>();
        try (InputStream in = new ClassPathResource("seed/roles.json").getInputStream()) {
            MAPPER.readTree(in).forEach((r) -> roleIdByName.put(normalize(r.path("name").asText()), r.path("id").asText()));
        }
        List<Entry> entries = load(codes, roleIdByName);
        Map<String, List<Entry>> byGenderPosition = new HashMap<>();
        for (Entry e : entries) {
            for (String p : e.player().getPositions()) {
                byGenderPosition.computeIfAbsent(e.gender() + "|" + p, (k) -> new ArrayList<>()).add(e);
            }
        }
        Map<String, Group> groups = new TreeMap<>();
        for (String key : SEEDS.vectorKeys()) {
            String[] parts = key.split("\\|");
            String position = parts[0];
            String role = parts[1];
            for (int gender = 0; gender <= 1; gender++) {
                List<Entry> pool = byGenderPosition.getOrDefault(gender + "|" + position, List.of());
                if (pool.isEmpty()) {
                    continue;
                }
                double[] levels = levelFits(pool, position, role, parts[2]);
                Group group = groups.computeIfAbsent(position + "|" + role, (k) -> new Group(position, role, new ArrayList<>(), new ArrayList<>(), new ArrayList<>()));
                for (int i = 0; i < pool.size(); i++) {
                    Integer level = pool.get(i).roles().get(position + "|" + role);
                    List<Double> target = level == null ? group.none() : level >= 2 ? group.plusPlus() : group.plus();
                    mergeMax(target, group, pool.get(i), levels[i], key);
                }
            }
        }
        writeReport(entries.size(), groups);
    }

    /** Aynı oyuncu için focus'lar arasında en yüksek levelFit kullanılır; kovada oyuncu başına tek değer tutulur. */
    private final Map<String, Double> best = new HashMap<>();

    private void mergeMax(List<Double> target, Group group, Entry entry, double level, String vectorKey) {
        String id = System.identityHashCode(entry.player()) + "#" + group.position() + "#" + group.role() + "#" + System.identityHashCode(target);
        Double previous = best.get(id);
        if (previous == null) {
            best.put(id, level);
            target.add(level);
            return;
        }
        if (level > previous) {
            target.remove(previous);
            target.add(level);
            best.put(id, level);
        }
    }

    private double[] levelFits(List<Entry> pool, String position, String role, String focus) {
        double[] fits = new double[pool.size()];
        int[] ovrs = new int[pool.size()];
        for (int i = 0; i < fits.length; i++) {
            fits[i] = ENGINE.currentFit(pool.get(i).player(), position, role, focus, Set.of()).currentFit();
            ovrs[i] = pool.get(i).player().getOverall();
        }
        int[] sortedOvrs = Arrays.stream(ovrs).sorted().toArray();
        Map<Integer, double[]> quantilesByOvr = new HashMap<>();
        double[] out = new double[fits.length];
        for (int i = 0; i < fits.length; i++) {
            double[] q = quantilesByOvr.computeIfAbsent(ovrs[i], (ovr) -> {
                LevelFitCalculator.Band band = LevelFitCalculator.band(sortedOvrs, ovr, MIN_BAND, HALF, MAX_HALF);
                List<Double> inBand = new ArrayList<>();
                for (int k = 0; k < fits.length; k++) {
                    if (ovrs[k] >= band.lo() && ovrs[k] <= band.hi()) {
                        inBand.add(fits[k]);
                    }
                }
                return LevelFitCalculator.quantiles(inBand.stream().mapToDouble(Double::doubleValue).toArray());
            });
            out[i] = LevelFitCalculator.smooth(q, fits[i]);
        }
        return out;
    }

    private List<Entry> load(JsonNode codes, Map<String, String> roleIdByName) throws Exception {
        List<String> lines = Files.readAllLines(DUMP, StandardCharsets.UTF_8);
        String[] header = lines.get(0).split(",");
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            idx.put(header[i], i);
        }
        List<Entry> entries = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) {
                continue;
            }
            String[] f = line.split(",", -1);
            List<String> positions = new ArrayList<>();
            for (int i = 1; i <= 7; i++) {
                String p = POSITION_CODES.get(Integer.parseInt(f[idx.get("preferredposition" + i)]));
                if (p != null && !positions.contains(p)) {
                    positions.add(p);
                }
            }
            if (positions.isEmpty()) {
                continue;
            }
            Map<Attr, Integer> attrs = new EnumMap<>(Attr.class);
            COLUMNS.forEach((a, col) -> attrs.put(a, Integer.parseInt(f[java.util.Objects.requireNonNull(idx.get(col), "kolon yok: " + col)])));
            int gender = Integer.parseInt(f[idx.get("gender")]);
            Map<String, Integer> roles = new HashMap<>();
            for (int i = 1; i <= 9; i++) {
                int code = Integer.parseInt(f[idx.get("role" + i)]);
                if (code == 0) {
                    continue;
                }
                JsonNode c = codes.path(String.valueOf(code % PLUS_PLUS));
                String roleId = roleIdByName.get(normalize(c.path("role").asText()));
                if (roleId != null) {
                    roles.put(c.path("position").asText() + "|" + roleId, code >= PLUS_PLUS ? 2 : 1);
                }
            }
            FitPlayer player = FitPlayer.builder().id(Long.parseLong(f[idx.get("playerid")])).overall(Integer.parseInt(f[idx.get("overallrating")])).positions(positions)
                    .attrs(attrs).playstyles(Map.of()).gender(gender).preferredFoot("1".equals(f[idx.get("preferredfoot")]) ? "Right" : "Left")
                    .weakFoot(Integer.parseInt(f[idx.get("weakfootabilitytypecode")])).skillMoves(Integer.parseInt(f[idx.get("skillmoves")]))
                    .heightCm(Integer.parseInt(f[idx.get("height")])).roleFamiliarity(Map.of()).build();
            entries.add(new Entry(player, gender, roles));
        }
        return entries;
    }

    /** AUC = P(pozitif levelFit > negatif levelFit) + 0,5·P(eşit); sıralama toplamıyla (Mann-Whitney) hesaplanır. */
    static double auc(List<Double> positives, List<Double> negatives) {
        if (positives.isEmpty() || negatives.isEmpty()) {
            return Double.NaN;
        }
        double[] neg = negatives.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        double wins = 0;
        for (double p : positives) {
            int lo = lowerBound(neg, p);
            int hi = upperBound(neg, p);
            wins += lo + 0.5 * (hi - lo);
        }
        return wins / ((double) positives.size() * neg.length);
    }

    private static int lowerBound(double[] a, double v) {
        int lo = 0;
        int hi = a.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (a[mid] < v) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private static int upperBound(double[] a, double v) {
        int lo = 0;
        int hi = a.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (a[mid] <= v) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private static double median(List<Double> values) {
        if (values.isEmpty()) {
            return Double.NaN;
        }
        double[] s = values.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        return s.length % 2 == 1 ? s[s.length / 2] : (s[s.length / 2 - 1] + s[s.length / 2]) / 2.0;
    }

    private void writeReport(int playerCount, Map<String, Group> groups) throws Exception {
        StringBuilder md = new StringBuilder();
        md.append("| Mevki · rol | n(++) | n(+) | n(rolsüz) | medyan levelFit ++ | medyan + | medyan rolsüz | AUC (+/++ vs rolsüz) | AUC (yalnız ++) |\n|---|---|---|---|---|---|---|---|---|\n");
        double weighted = 0;
        double weight = 0;
        double weightedPlusPlus = 0;
        double weightPlusPlus = 0;
        List<String> weak = new ArrayList<>();
        List<Group> sorted = new ArrayList<>(groups.values());
        sorted.sort(Comparator.comparing(Group::position).thenComparing(Group::role));
        for (Group g : sorted) {
            List<Double> positives = new ArrayList<>(g.plusPlus());
            positives.addAll(g.plus());
            if (positives.size() < MIN_GROUP || g.none().size() < MIN_GROUP) {
                continue;
            }
            double auc = auc(positives, g.none());
            weighted += auc * positives.size();
            weight += positives.size();
            if (auc < WEAK_AUC) {
                weak.add(g.position() + " " + g.role() + " (AUC " + String.format("%.3f", auc) + ", n+=" + positives.size() + ")");
            }
            double aucPlusPlus = auc(g.plusPlus(), g.none());
            if (!Double.isNaN(aucPlusPlus)) {
                weightedPlusPlus += aucPlusPlus * g.plusPlus().size();
                weightPlusPlus += g.plusPlus().size();
            }
            md.append(String.format("| %s · %s | %d | %d | %d | %.1f | %.1f | %.1f | %.3f | %.3f |%n", g.position(), g.role(), g.plusPlus().size(), g.plus().size(), g.none().size(),
                    median(g.plusPlus()), median(g.plus()), median(g.none()), auc, aucPlusPlus));
        }
        md.append(String.format("%nOyuncu: %d · ağırlıklı ortalama AUC (+/++): %.3f · yalnız ++ ağırlıklı AUC: %.3f · hedef ≥ %.2f · AUC < %.1f olanlar: %s%n", playerCount, weight == 0 ? Double.NaN : weighted / weight, weightPlusPlus == 0 ? Double.NaN : weightedPlusPlus / weightPlusPlus,
                TARGET_AUC, WEAK_AUC, weak.isEmpty() ? "yok" : String.join("; ", weak)));
        Path out = Path.of("target", "role-validation.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, md.toString(), StandardCharsets.UTF_8);
    }
}
