package com.fcareer.development.plan;

import com.fcareer.development.plan.DevelopmentPlans.Kind;
import com.fcareer.development.plan.DevelopmentPlans.Plan;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.norms.RoleSeeds;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * FIT-3 madde 8: gelişim planları ↔ seed vektörleri doğrulama raporu. Yalnız rapor; vektör kalibrasyonu ayrı karar, otomatik değişiklik yok.
 * - Mevki: mevki planı attribute'ları ↔ `positionVectors[mevki]` en ağır 10.
 * - Rol: rol planı ↔ (mevki, rol) vektörü (o mevkideki odakların ortalaması) en ağır 10.
 * - Rolün farkı: `extraVsPosition` ↔ rol vektörünün mevki vektöründen en çok ayrıştığı (pozitif fark) attribute'lar.
 */
@Service
@RequiredArgsConstructor
public class DevelopmentPlanReport {

    private static final int TOP = 10;
    /** Rolün farkı için en az bu kadar ayrışan attribute karşılaştırılır (extra daha çoksa onun sayısı). */
    private static final int MIN_DIVERGENT = 3;

    private final DevelopmentPlans plans;
    private final RoleSeeds seeds;

    public record Row(String plan, String position, List<Attr> planAttributes, List<Attr> top10, List<Attr> common, List<Attr> onlyPlan, List<Attr> onlyVector,
                      double overlap) {
    }

    public record Difference(String plan, String position, List<Attr> extra, List<Attr> divergent, List<Attr> common) {
    }

    public record Report(List<Row> positions, List<Row> roles, List<Difference> differences) {
    }

    public Report build() {
        List<Row> positionRows = plans.all().stream().filter((p) -> p.kind() == Kind.POSITION)
                .flatMap((p) -> p.positions().stream().map((pos) -> row(p, pos, seeds.positionVector(pos)))).toList();
        List<Row> roleRows = plans.all().stream().filter((p) -> p.kind() == Kind.ROLE)
                .flatMap((p) -> p.positions().stream().map((pos) -> row(p, pos, roleVector(pos, p.baseRole())))).toList();
        List<Difference> differences = plans.all().stream().filter((p) -> p.kind() == Kind.ROLE)
                .flatMap((p) -> p.positions().stream().map((pos) -> difference(p, pos))).toList();
        return new Report(positionRows, roleRows, differences);
    }

    /** (mevki, rol) vektörü: o mevkide tanımlı odak vektörlerinin ortalaması; hiç yoksa mevki vektörü. */
    private Map<Attr, Double> roleVector(String position, String baseRole) {
        String prefix = position + "|" + baseRole + "|";
        List<Map<Attr, Double>> focuses = seeds.vectorKeys().stream().filter((k) -> k.startsWith(prefix))
                .map((k) -> seeds.vector(position, baseRole, k.substring(prefix.length())).orElseThrow()).toList();
        if (focuses.isEmpty()) {
            return seeds.positionVector(position);
        }
        Map<Attr, Double> mean = new EnumMap<>(Attr.class);
        focuses.forEach((v) -> v.forEach((a, w) -> mean.merge(a, w / focuses.size(), Double::sum)));
        return mean;
    }

    private static List<Attr> top(Map<Attr, Double> vector, int n) {
        return vector.entrySet().stream().sorted(Map.Entry.<Attr, Double>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(n).map(Map.Entry::getKey).toList();
    }

    private static Row row(Plan plan, String position, Map<Attr, Double> vector) {
        List<Attr> top10 = top(vector, TOP);
        List<Attr> common = plan.attributes().stream().filter(top10::contains).toList();
        List<Attr> onlyPlan = plan.attributes().stream().filter((a) -> !top10.contains(a)).toList();
        List<Attr> onlyVector = top10.stream().filter((a) -> !plan.attributes().contains(a)).toList();
        double overlap = plan.attributes().isEmpty() ? 0 : (double) common.size() / plan.attributes().size();
        return new Row(plan.id(), position, plan.attributes(), top10, common, onlyPlan, onlyVector, overlap);
    }

    private Difference difference(Plan plan, String position) {
        List<Attr> extra = plans.extraVsPosition(plan);
        Map<Attr, Double> role = roleVector(position, plan.baseRole());
        Map<Attr, Double> pos = seeds.positionVector(position);
        Map<Attr, Double> delta = new EnumMap<>(Attr.class);
        role.forEach((a, w) -> {
            double d = w - pos.getOrDefault(a, 0.0);
            if (d > 0) {
                delta.put(a, d);
            }
        });
        List<Attr> divergent = top(delta, Math.max(MIN_DIVERGENT, extra.size()));
        return new Difference(plan.id(), position, extra, divergent, extra.stream().filter(divergent::contains).toList());
    }

    /** Buğra'ya verilecek tablo: yalnız uyuşmazlığı olan satırlar ayrıntılı. */
    public String markdown(Report report) {
        StringBuilder md = new StringBuilder("# Gelişim planları ↔ seed vektörleri (FIT-3 madde 8)\n\nOtomatik değişiklik yok; vektör kalibrasyonu ayrı karar.\n\n");
        md.append("## Mevki planları (mevki vektörünün en ağır 10'u)\n\n| Mevki planı | Mevki | Örtüşme | Planda var, vektörde yok | Vektörde ağır, planda yok |\n|---|---|---|---|---|\n");
        report.positions().forEach((r) -> md.append(rowLine(r)));
        md.append("\n## Rol planları ((mevki, rol) vektörünün en ağır 10'u, odak ortalaması)\n\n| Rol planı | Mevki | Örtüşme | Planda var, vektörde yok | Vektörde ağır, planda yok |\n|---|---|---|---|---|\n");
        report.roles().stream().sorted(Comparator.comparingDouble(Row::overlap)).forEach((r) -> md.append(rowLine(r)));
        md.append("\n## Rolün farkı (extraVsPosition ↔ rol vektörünün mevkiden ayrıştığı attribute'lar)\n\n| Rolün farkı | Mevki | extra | Vektörde ayrışan | Ortak |\n|---|---|---|---|---|\n");
        report.differences().forEach((d) -> md.append("| ").append(d.plan()).append(" | ").append(d.position()).append(" | ").append(keys(d.extra())).append(" | ")
                .append(keys(d.divergent())).append(" | ").append(keys(d.common())).append(" |\n"));
        return md.toString();
    }

    private static String rowLine(Row r) {
        return "| " + r.plan() + " | " + r.position() + " | " + String.format(Locale.ROOT, "%d/%d", r.common().size(), r.planAttributes().size()) + " | "
                + keys(r.onlyPlan()) + " | " + keys(r.onlyVector()) + " |\n";
    }

    private static String keys(List<Attr> attrs) {
        return attrs.isEmpty() ? "—" : attrs.stream().map(Attr::getKey).collect(Collectors.joining(", "));
    }
}
