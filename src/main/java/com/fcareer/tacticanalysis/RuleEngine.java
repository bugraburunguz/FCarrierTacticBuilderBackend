package com.fcareer.tacticanalysis;

import com.fcareer.tacticanalysis.TacticAnalysisModels.Evidence;
import com.fcareer.tacticanalysis.TacticAnalysisModels.Finding;
import com.fcareer.tacticanalysis.TacticAnalysisModels.NotEvaluated;
import com.fcareer.tacticanalysis.TacticRules.Condition;
import com.fcareer.tacticanalysis.TacticRules.RuleDef;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Kurallar = metrikler üzerinde predikat (13 §3.4). Her bulgu: kod, şiddet, sayısal kanıt (değer + eşik) ve öneri; metin yalnız bu sayılardan şablonla üretilir.
 * Oyuncu gerektiren kural oyuncu yokken "değerlendirilmedi" olarak ayrıca raporlanır (sessizce atlanmaz).
 */
@Component
public class RuleEngine {

    private static final String REQUIRES_PLAYERS = "players";
    private static final List<Map<String, String>> SINGLE = List.of(Map.of());

    public record Result(List<Finding> findings, List<NotEvaluated> notEvaluated) {
    }

    private final TacticRules rules;
    private final RuleMetrics metrics;

    public RuleEngine(TacticRules rules, RuleMetrics metrics) {
        this.rules = rules;
        this.metrics = metrics;
    }

    public Result evaluate(TacticContext ctx) {
        List<Finding> findings = new ArrayList<>();
        List<NotEvaluated> skipped = new ArrayList<>();
        java.util.Set<String> produced = new java.util.HashSet<>();
        for (RuleDef rule : rules.rules()) {
            if (rule.suppressedBy().stream().anyMatch(produced::contains)) {
                continue;
            }
            if (rule.requires().contains(REQUIRES_PLAYERS) && !ctx.hasPlayers()) {
                skipped.add(new NotEvaluated(rule.id(), "oyuncu atanmamış: kural oyuncu attribute'ları gerektirir"));
                continue;
            }
            for (Map<String, String> vars : rule.foreach().isEmpty() ? SINGLE : rule.foreach()) {
                evaluate(rule, vars, ctx).ifPresent((f) -> {
                    findings.add(f);
                    produced.add(rule.id());
                });
            }
        }
        // Bastıran kural seed'de sonra gelse de geçerli (FIT-2: counter_risk_overlap → counter_risk); sıra bağımlılığı yok.
        Map<String, List<String>> suppressedBy = new java.util.HashMap<>();
        rules.rules().forEach((r) -> suppressedBy.put(r.id(), r.suppressedBy()));
        findings.removeIf((f) -> suppressedBy.getOrDefault(f.code(), List.of()).stream().anyMatch(produced::contains));
        return new Result(findings, skipped);
    }

    private java.util.Optional<Finding> evaluate(RuleDef rule, Map<String, String> vars, TacticContext ctx) {
        List<Evidence> evidence = new ArrayList<>();
        for (Condition c : rule.when()) {
            Map<String, Object> params = substitute(c.params(), vars);
            OptionalDouble value = metrics.value(c.metric(), params, ctx);
            Resolved t = resolve(c, ctx);
            if (value.isEmpty() || !holds(c.op(), value.getAsDouble(), t.threshold())) {
                return java.util.Optional.empty();
            }
            evidence.add(new Evidence(c.metric(), params, round(value.getAsDouble()), c.op(), round(t.threshold()), t.reference(), t.margin()));
        }
        if (!rule.whenAny().isEmpty()) {
            boolean any = false;
            for (Condition c : rule.whenAny()) {
                Map<String, Object> params = substitute(c.params(), vars);
                OptionalDouble value = metrics.value(c.metric(), params, ctx);
                Resolved t = resolve(c, ctx);
                if (value.isPresent() && holds(c.op(), value.getAsDouble(), t.threshold())) {
                    evidence.add(new Evidence(c.metric(), params, round(value.getAsDouble()), c.op(), round(t.threshold()), t.reference(), t.margin()));
                    any = true;
                }
            }
            if (!any) {
                return java.util.Optional.empty();
            }
        }
        return java.util.Optional.of(new Finding(rule.id(), rule.title(), rule.severity(), vars, evidence, rule.recommendation(), text(rule.text(), evidence, vars, ctx)));
    }

    /** Göreli koşulda eşik = referans − marj (referans bağlamda varsa); yoksa mutlak değer. */
    private record Resolved(double threshold, Double reference, Double margin) {
    }

    private Resolved resolve(Condition c, TacticContext ctx) {
        if (c.ref() != null && ctx.refs().containsKey(c.ref())) {
            double ref = ctx.refs().get(c.ref());
            double margin = rules.margin(c.marginKey());
            return new Resolved(ref - margin, ref, margin);
        }
        return new Resolved(c.value(), null, null);
    }

    private static Map<String, Object> substitute(Map<String, Object> params, Map<String, String> vars) {
        Map<String, Object> out = new LinkedHashMap<>();
        params.forEach((k, v) -> {
            String s = v instanceof String str ? str : null;
            if (s != null && s.startsWith("${") && s.endsWith("}")) {
                out.put(k, vars.get(s.substring(2, s.length() - 1)));
            } else {
                out.put(k, v);
            }
        });
        return out;
    }

    static boolean holds(String op, double value, double threshold) {
        return switch (op) {
            case "LT" -> value < threshold;
            case "LTE" -> value <= threshold;
            case "GT" -> value > threshold;
            case "GTE" -> value >= threshold;
            case "EQ" -> Math.abs(value - threshold) < 1e-9;
            default -> throw new IllegalArgumentException("bilinmeyen operatör: " + op);
        };
    }

    private static final java.util.regex.Pattern REL = java.util.regex.Pattern.compile("\\{rel(\\d+):([^}]*)\\}");

    /** Kullanıcı metni: yalnız kanıt sayılarından şablonla. {e0}/{t0}: değer/eşik; {rel1:ad}: " · <seviye> <ad> <referans> · marj <m>" (yalnız göreli kanıtta); {bandName}, {buildUpName}, {level}. */
    private static String text(String template, List<Evidence> evidence, Map<String, String> vars, TacticContext ctx) {
        String out = template;
        java.util.regex.Matcher m = REL.matcher(out);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            int i = Integer.parseInt(m.group(1));
            Evidence e = i < evidence.size() ? evidence.get(i) : null;
            String rel = e == null || e.reference() == null ? "" : " · " + (ctx.levelLabel() == null ? "" : ctx.levelLabel() + " ") + m.group(2) + " " + format(e.reference()) + " · marj " + format(e.margin());
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(rel));
        }
        m.appendTail(sb);
        out = sb.toString();
        for (int i = 0; i < evidence.size(); i++) {
            out = out.replace("{e" + i + "}", format(evidence.get(i).value())).replace("{t" + i + "}", format(evidence.get(i).threshold()));
        }
        out = out.replace("{bandName}", BAND_NAMES.get(ctx.band())).replace("{buildUpName}", BUILD_UP_NAMES.get(ctx.buildUp()));
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue());
        }
        return out;
    }

    private static final Map<AnalysisBand, String> BAND_NAMES = Map.of(AnalysisBand.DEEP, "Deep", AnalysisBand.BALANCED, "Balanced", AnalysisBand.HIGH, "High", AnalysisBand.AGGRESSIVE, "Aggressive");
    private static final Map<BuildUpType, String> BUILD_UP_NAMES = Map.of(BuildUpType.SHORT, "Short Passing", BuildUpType.BALANCED, "Balanced", BuildUpType.COUNTER, "Counter");

    private static String format(double v) {
        String s = String.format(Locale.ROOT, "%.2f", v);
        return s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
