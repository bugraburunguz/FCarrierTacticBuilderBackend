package com.fcareer.fitengine.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * FIT-2 madde 7: kurulum kontrolleri `seed/setup-checks.fc27.json`'dan (deklaratif; motor SetupCheckService). Hat bandı (depthMin/depthMax) ve build-up
 * öneki (`short`, `counter`) kontrolü seçer; eşik hatta göre basamaklanabilir (`thresholdSteps`). Değerler verified:false.
 */
@Component
public class SetupCheckRules {

    private static final String SEED = "seed/setup-checks.fc27.json";

    /** Kontrolün ölçtüğü oyuncu değeri. */
    public enum Metric {
        PACE((p) -> (p.attr(Attr.ACCELERATION) + p.attr(Attr.SPRINT_SPEED)) / 2),
        AIR((p) -> (p.attr(Attr.HEADING) + p.attr(Attr.JUMPING) + p.attr(Attr.STRENGTH)) / 3),
        PASSING((p) -> (p.attr(Attr.SHORT_PASSING) + p.attr(Attr.BALL_CONTROL)) / 2),
        DEF_AWARENESS((p) -> p.attr(Attr.DEF_AWARENESS)),
        GK_KICKING((p) -> p.attr(Attr.GK_KICKING));

        private final ToIntFunction<FitPlayer> value;

        Metric(ToIntFunction<FitPlayer> value) {
            this.value = value;
        }

        public int of(FitPlayer player) {
            return value.applyAsInt(player);
        }
    }

    public record Step(int depthMin, int threshold) {
    }

    public record Check(String id, String title, String detail, String advice, Integer depthMin, Integer depthMax, String buildUp, Set<String> positions,
                        int needed, Metric metric, int threshold, List<Step> thresholdSteps) {

        public boolean appliesTo(String buildUpName, int depth) {
            boolean depthOk = (Objects.isNull(depthMin) || depth >= depthMin) && (Objects.isNull(depthMax) || depth <= depthMax);
            return depthOk && (Objects.isNull(buildUp) || buildUpName.toLowerCase(Locale.ROOT).startsWith(buildUp));
        }

        /** Hatta göre eşik: karşılanan en yüksek basamak, yoksa temel eşik. */
        public int thresholdAt(int depth) {
            return thresholdSteps.stream().filter((s) -> depth >= s.depthMin()).reduce((a, b) -> b).map(Step::threshold).orElse(threshold);
        }

        public String detailAt(int depth) {
            return detail.replace("{threshold}", String.valueOf(thresholdAt(depth))).replace("{needed}", String.valueOf(needed));
        }
    }

    private final boolean verified;
    private final List<Check> checks = new ArrayList<>();

    public SetupCheckRules(ObjectMapper mapper) {
        try (InputStream in = new ClassPathResource(SEED).getInputStream()) {
            JsonNode root = mapper.readTree(in);
            verified = root.path("verified").asBoolean(false);
            for (JsonNode n : root.path("checks")) {
                List<String> positions = new ArrayList<>();
                n.path("positions").forEach((x) -> positions.add(x.asText()));
                List<Step> steps = new ArrayList<>();
                n.path("thresholdSteps").forEach((s) -> steps.add(new Step(s.path("depthMin").asInt(), s.path("threshold").asInt())));
                checks.add(new Check(n.path("id").asText(), n.path("title").asText(), n.path("detail").asText(), n.path("advice").asText(),
                        n.has("depthMin") ? n.path("depthMin").asInt() : null, n.has("depthMax") ? n.path("depthMax").asInt() : null,
                        n.has("buildUp") ? n.path("buildUp").asText() : null, Set.copyOf(positions), n.path("needed").asInt(),
                        Metric.valueOf(n.path("metric").asText()), n.path("threshold").asInt(), List.copyOf(steps)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public boolean verified() {
        return verified;
    }

    public List<Check> checks() {
        return List.copyOf(checks);
    }

    /** Seed sırasıyla: önce hat kontrolü, sonra build-up kontrolleri. */
    public List<Check> applicable(String buildUp, int depth) {
        return checks.stream().filter((c) -> c.appliesTo(buildUp, depth)).toList();
    }
}
