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
        for (RuleDef rule : rules.rules()) {
            if (rule.requires().contains(REQUIRES_PLAYERS) && !ctx.hasPlayers()) {
                skipped.add(new NotEvaluated(rule.id(), "oyuncu atanmamış: kural oyuncu attribute'ları gerektirir"));
                continue;
            }
            for (Map<String, String> vars : rule.foreach().isEmpty() ? SINGLE : rule.foreach()) {
                evaluate(rule, vars, ctx).ifPresent(findings::add);
            }
        }
        return new Result(findings, skipped);
    }

    private java.util.Optional<Finding> evaluate(RuleDef rule, Map<String, String> vars, TacticContext ctx) {
        List<Evidence> evidence = new ArrayList<>();
        for (Condition c : rule.when()) {
            Map<String, Object> params = substitute(c.params(), vars);
            OptionalDouble value = metrics.value(c.metric(), params, ctx);
            if (value.isEmpty() || !holds(c.op(), value.getAsDouble(), c.value())) {
                return java.util.Optional.empty();
            }
            evidence.add(new Evidence(c.metric(), params, round(value.getAsDouble()), c.op(), c.value()));
        }
        return java.util.Optional.of(new Finding(rule.id(), rule.severity(), vars, evidence, rule.recommendation(), text(rule.text(), evidence, vars)));
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

    private static String text(String template, List<Evidence> evidence, Map<String, String> vars) {
        String out = template;
        for (int i = 0; i < evidence.size(); i++) {
            out = out.replace("{e" + i + "}", format(evidence.get(i).value())).replace("{t" + i + "}", format(evidence.get(i).threshold()));
        }
        Map<String, String> all = new HashMap<>(vars);
        for (Map.Entry<String, String> e : all.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue());
        }
        return out;
    }

    private static String format(double v) {
        String s = String.format(Locale.ROOT, "%.2f", v);
        return s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
