package com.fcareer.fitengine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.norms.RoleSeeds;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** E3-4 CI değişmezleri (13 §3.5 veri değişmezleri): rol vektörleri, temel attribute'lar, PlayStyle. */
class RoleSeedInvariantsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final double FOCUS_COSINE_MAX = 0.95;
    private static final double ROLE_COSINE_MAX = 0.90;
    private static final double MAX_SINGLE_ATTRIBUTE_SHARE = 0.25;
    /** Kaleci ailesi muaf (4-5 kaleci attribute'undan ibaret; %25 sınırı vektörleri eşitler ve kosinüs değişmezini bozar). Gerekçe: E3-6b raporu. */
    private static final Set<String> CAP_EXEMPT_ROLES = Set.of("goalkeeper", "sweeper_keeper", "ball_playing_keeper");
    private static final Set<Attr> ATTACKING = Set.of(Attr.FINISHING, Attr.POSITIONING, Attr.SHOT_POWER, Attr.LONG_SHOTS, Attr.VOLLEYS, Attr.PENALTIES);
    private static final Set<String> HIGH_WORKLOAD = Set.of("box_to_box", "wingback", "attacking_wingback", "half_winger");

    private final RoleSeeds seeds = new RoleSeeds(MAPPER);

    private JsonNode roles() throws Exception {
        try (InputStream in = new ClassPathResource("seed/roles.json").getInputStream()) {
            return MAPPER.readTree(in);
        }
    }

    private static double cosine(Map<Attr, Double> a, Map<Attr, Double> b) {
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (Attr attr : Attr.values()) {
            double x = a.getOrDefault(attr, 0.0);
            double y = b.getOrDefault(attr, 0.0);
            dot += x * y;
            na += x * x;
            nb += y * y;
        }
        return dot / Math.sqrt(na * nb);
    }

    @Test
    void theSeedHashesMatchTheirContentAndAllAreMarkedUnverified() {
        for (String name : RoleSeeds.FILES) {
            assertThat(seeds.hash(name)).as(name).isEqualTo(seeds.expectedHash(name));
            assertThat(seeds.document(name).path("verified").asBoolean(true)).as(name + " verified").isFalse();
        }
    }

    @Test
    void everyPositionRoleFocusHasANormalizedNonNegativeVector() throws Exception {
        int count = 0;
        for (JsonNode role : roles()) {
            for (JsonNode position : role.path("positions")) {
                for (JsonNode focus : role.path("focuses")) {
                    Map<Attr, Double> v = seeds.vector(position.asText(), role.path("id").asText(), focus.asText())
                            .orElseThrow(() -> new AssertionError(position + " " + role.path("id") + " " + focus));
                    assertThat(v.values().stream().mapToDouble(Double::doubleValue).sum()).isBetween(0.999, 1.001);
                    assertThat(v.values()).allMatch((w) -> w >= 0);
                    count++;
                }
            }
        }
        assertThat(count).isEqualTo(seeds.vectorKeys().size());
    }

    @Test
    void theEaOverallRegressionIsAtMostHalfOfEveryVectorMass() {
        seeds.vectorKeys().forEach((key) -> assertThat(seeds.ovrMass(key)).as(key).isLessThanOrEqualTo(0.5));
    }

    @Test
    void focusVariantsAndDifferentRolesStayDistinguishable() throws Exception {
        for (JsonNode first : roles()) {
            for (JsonNode position : first.path("positions")) {
                for (JsonNode second : roles()) {
                    if (!second.path("positions").toString().contains(position.asText())) {
                        continue;
                    }
                    for (JsonNode f1 : first.path("focuses")) {
                        for (JsonNode f2 : second.path("focuses")) {
                            String id1 = first.path("id").asText();
                            String id2 = second.path("id").asText();
                            if (id1.equals(id2) && f1.asText().equals(f2.asText())) {
                                continue;
                            }
                            double c = cosine(seeds.vector(position.asText(), id1, f1.asText()).orElseThrow(), seeds.vector(position.asText(), id2, f2.asText()).orElseThrow());
                            double limit = id1.equals(id2) ? FOCUS_COSINE_MAX : ROLE_COSINE_MAX;
                            assertThat(c).as(position.asText() + " " + id1 + ":" + f1.asText() + " ~ " + id2 + ":" + f2.asText()).isLessThanOrEqualTo(limit);
                        }
                    }
                }
            }
        }
    }

    @Test
    void noTwoVectorsAreIdentical() {
        Set<Map<Attr, Double>> seen = new HashSet<>();
        for (String key : seeds.vectorKeys()) {
            String[] parts = key.split("\\|");
            assertThat(seen.add(seeds.vector(parts[0], parts[1], parts[2]).orElseThrow())).as(key).isTrue();
        }
    }

    @Test
    void baseAttributesFollowTheDocumentedRules() throws Exception {
        for (JsonNode role : roles()) {
            String id = role.path("id").asText();
            List<RoleSeeds.BaseAttr> base = seeds.baseAttrs(id);
            assertThat(base).as(id).isNotEmpty().hasSizeLessThanOrEqualTo(4);
            boolean defensive = List.of("CB", "GK").contains(role.path("weightGroup").asText());
            for (RoleSeeds.BaseAttr b : base) {
                if (defensive) {
                    assertThat(ATTACKING).as(id + " savunma rolünde hücum attribute'u temel olamaz (ENG-18)").doesNotContain(b.attr());
                }
                if (b.attr() == Attr.STAMINA) {
                    assertThat(HIGH_WORKLOAD).as(id + " Stamina tabanı yalnız yüksek iş yüklü rollerde").contains(id);
                }
                assertThat(b.minZ()).isLessThanOrEqualTo(b.targetZ());
            }
        }
    }

    @Test
    void playstyleBonusIsCappedAtEightPoints() throws Exception {
        int multiplier = seeds.modifiers().path("psCap").path("psPlusMultiplier").asInt();
        int cap = seeds.modifiers().path("psCap").path("maxTotal").asInt();
        List<String> over = new ArrayList<>();
        for (JsonNode role : roles()) {
            int total = seeds.playstyles(role.path("id").asText()).stream().mapToInt((p) -> p.weight() * multiplier).sum();
            if (total > cap) {
                over.add(role.path("id").asText() + "=" + total);
            }
        }
        assertThat(over).isEmpty();
    }

    /** E3-6b (Buğra kararı): hiçbir rol/focus vektöründe tek bir attribute'un ağırlığı vektör kütlesinin %25'ini geçemez (tek özellik baskınlığı yok). */
    @Test
    void noSingleAttributeExceedsAQuarterOfAnyRoleVector() {
        List<String> violations = new ArrayList<>();
        for (String key : seeds.vectorKeys()) {
            String[] parts = key.split("[|]");
            if (CAP_EXEMPT_ROLES.contains(parts[1])) {
                continue;
            }
            Map<Attr, Double> v = seeds.vector(parts[0], parts[1], parts[2]).orElseThrow();
            v.forEach((attr, weight) -> {
                if (weight > MAX_SINGLE_ATTRIBUTE_SHARE + 1e-6) {
                    violations.add(key + " " + attr.getKey() + "=" + String.format("%.3f", weight));
                }
            });
        }
        assertThat(violations).as("tek attribute %25'i geçemez").isEmpty();
    }
}
