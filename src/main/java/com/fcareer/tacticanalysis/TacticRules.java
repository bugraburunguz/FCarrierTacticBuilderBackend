package com.fcareer.tacticanalysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.tacticanalysis.TacticAnalysisModels.Recommendation;
import com.fcareer.tacticanalysis.TacticAnalysisModels.Severity;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** `seed/tactic-rules.fc27.json` (E3-7): metrikler üzerinde predikat + eşik + kanıt + öneri. Etiket tabanlı eski kurallar yoktur. Eşikler verified:false. */
@Component
public class TacticRules {

    private static final String SEED = "seed/tactic-rules.fc27.json";

    /** metric: RuleMetrics adı; params: örn. {"side":"${side}"}; op: LT LTE GT GTE EQ. */
    /** ref: göreli eşik için referans metriği (örn. CB_PACE_REF); marginKey: params.margins içindeki marj; referans yoksa mutlak `value` kullanılır. */
    public record Condition(String metric, Map<String, Object> params, String op, double value, String ref, String marginKey) {
    }

    /** whenAny: en az biri sağlanmalı (when ile birlikte VE); suppressedBy: listelenen kurallardan biri bulgu ürettiyse bu kural atlanır. foreach: örnek değişken kümeleri (örn. side=LEFT, sideTr=sol); requires: ["players"] → oyuncu yoksa değerlendirilmez. text: {e0},{t0} kanıt değer/eşik, {sideTr}. */
    public record RuleDef(String id, String title, Severity severity, List<Map<String, String>> foreach, List<String> requires, List<Condition> when, List<Condition> whenAny, List<String> suppressedBy, String text, Recommendation recommendation) {
    }

    private final TacticParams params;
    private final Map<String, Double> margins = new HashMap<>();
    private final List<RuleDef> rules = new ArrayList<>();
    private final boolean verified;

    public TacticRules(ObjectMapper mapper) {
        try (InputStream in = new ClassPathResource(SEED).getInputStream()) {
            JsonNode root = mapper.readTree(in);
            verified = root.path("verified").asBoolean(true);
            params = TacticParams.from(root.path("params"));
            root.path("params").path("margins").fields().forEachRemaining((e) -> margins.put(e.getKey(), e.getValue().asDouble()));
            for (JsonNode r : root.path("rules")) {
                List<Map<String, String>> foreach = new ArrayList<>();
                r.path("foreach").forEach((n) -> {
                    Map<String, String> vars = new HashMap<>();
                    n.fields().forEachRemaining((e) -> vars.put(e.getKey(), e.getValue().asText()));
                    foreach.add(vars);
                });
                List<String> requires = new ArrayList<>();
                r.path("requires").forEach((n) -> requires.add(n.asText()));
                List<Condition> when = new ArrayList<>();
                for (JsonNode c : r.path("when")) {
                    when.add(condition(c));
                }
                List<Condition> whenAny = new ArrayList<>();
                for (JsonNode c : r.path("whenAny")) {
                    whenAny.add(condition(c));
                }
                List<String> suppressed = new ArrayList<>();
                r.path("suppressedBy").forEach((n) -> suppressed.add(n.asText()));
                rules.add(new RuleDef(r.path("id").asText(), r.path("title").asText(), Severity.valueOf(r.path("severity").asText()), foreach, requires, when, whenAny, suppressed, r.path("text").asText(),
                        new Recommendation(r.path("recommendation").path("code").asText(), r.path("recommendation").path("text").asText())));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Condition condition(JsonNode c) {
        Map<String, Object> cp = new HashMap<>();
        c.path("params").fields().forEachRemaining((e) -> cp.put(e.getKey(), e.getValue().isNumber() ? (Object) e.getValue().asDouble() : e.getValue().asText()));
        return new Condition(c.path("metric").asText(), cp, c.path("op").asText(), c.path("value").asDouble(), c.path("ref").asText(null), c.path("marginKey").asText(null));
    }

    public double margin(String key) {
        return margins.getOrDefault(key, 0.0);
    }

    public TacticParams params() {
        return params;
    }

    public List<RuleDef> rules() {
        return rules;
    }

    public boolean verified() {
        return verified;
    }
}
