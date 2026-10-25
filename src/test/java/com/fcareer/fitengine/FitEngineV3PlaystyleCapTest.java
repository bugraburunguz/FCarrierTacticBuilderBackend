package com.fcareer.fitengine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.norms.AttrNorms;
import com.fcareer.fitengine.norms.PosDist;
import com.fcareer.fitengine.norms.RoleSeeds;
import com.fcareer.fitengine.service.FitEngineV3;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * FIT-1 madde 4: PlayStyle toplamı tavana (psCap.maxTotal) kırpılınca "Nedenler" listesi kırpılmamış tek tek puanları yazıyordu; etki
 * abartılıyordu. Kırpma olunca katkılar orantılı ölçeklenir: playstyle katkılarının toplamı gerçek etkiye (tavan) eşittir.
 */
class FitEngineV3PlaystyleCapTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** Bugünkü seed'de bir rolün tüm PS+'ları en çok tavana (8) eşit; kırpmayı sınamak için tavan düşürülür. */
    private static final double TEST_CAP = 5;
    private static final RoleSeeds SEEDS = new RoleSeeds(MAPPER) {
        @Override
        public JsonNode modifiers() {
            com.fasterxml.jackson.databind.node.ObjectNode copy = super.modifiers().deepCopy();
            ((com.fasterxml.jackson.databind.node.ObjectNode) copy.path("psCap")).put("maxTotal", TEST_CAP);
            return copy;
        }
    };
    private static final FitEngineV3 ENGINE = new FitEngineV3(new AttrNorms(MAPPER), new PosDist(MAPPER), SEEDS);

    @Test
    void cappedPlaystyleContributionsSumToTheCapAndKeepTheirProportions() throws Exception {
        double cap = SEEDS.modifiers().path("psCap").path("maxTotal").asDouble();
        int checked = 0;
        try (InputStream in = new ClassPathResource("seed/roles.json").getInputStream()) {
            for (JsonNode role : MAPPER.readTree(in)) {
                String baseRole = role.path("id").asText();
                List<RoleSeeds.RolePlaystyle> styles = SEEDS.playstyles(baseRole);
                double raw = styles.stream().mapToDouble((s) -> s.weight() * 2.0).sum();
                if (raw <= cap) {
                    continue;
                }
                String position = role.path("positions").get(0).asText();
                String focus = role.path("focuses").get(0).asText();
                Map<String, Integer> all = new HashMap<>();
                styles.forEach((s) -> all.put(s.playstyle(), FitPlayer.PLAYSTYLE_PLUS));
                List<FitEngineV3.Contribution> ps = ENGINE.currentFit(player(position, all), position, baseRole, focus, Set.of()).contributions().stream()
                        .filter((c) -> c.term().equals("playstyle")).toList();
                assertThat(ps.stream().mapToDouble(FitEngineV3.Contribution::points).sum()).as(baseRole).isCloseTo(cap, within(1e-9));
                double scale = cap / raw;
                for (FitEngineV3.Contribution c : ps) {
                    int weight = styles.stream().filter((s) -> s.playstyle().equals(c.key())).findFirst().orElseThrow().weight();
                    assertThat(c.points()).as(baseRole + " " + c.key()).isCloseTo(weight * 2.0 * scale, within(1e-9));
                }
                checked++;
            }
        }
        assertThat(checked).as("tavanı aşan en az bir rol").isPositive();
    }

    @Test
    void uncappedPlaystyleContributionsAreUnchanged() throws Exception {
        try (InputStream in = new ClassPathResource("seed/roles.json").getInputStream()) {
            JsonNode role = null;
            RoleSeeds.RolePlaystyle one = null;
            for (JsonNode candidate : MAPPER.readTree(in)) {
                one = SEEDS.playstyles(candidate.path("id").asText()).stream().filter((s) -> s.weight() <= TEST_CAP).findFirst().orElse(null);
                if (one != null) {
                    role = candidate;
                    break;
                }
            }
            String baseRole = role.path("id").asText();
            String position = role.path("positions").get(0).asText();
            List<FitEngineV3.Contribution> ps = ENGINE.currentFit(player(position, Map.of(one.playstyle(), 1)), position, baseRole, role.path("focuses").get(0).asText(), Set.of())
                    .contributions().stream().filter((c) -> c.term().equals("playstyle")).toList();
            int weight = one.weight();
            assertThat(ps).singleElement().satisfies((c) -> assertThat(c.points()).isEqualTo(weight));
        }
    }

    private static FitPlayer player(String position, Map<String, Integer> playstyles) {
        Map<Attr, Integer> attrs = new EnumMap<>(Attr.class);
        for (Attr attr : Attr.values()) {
            attrs.put(attr, 70);
        }
        return FitPlayer.builder().id(1L).name("p").overall(75).potential(80).valueEur(1_000_000L).positions(List.of(position))
                .attrs(attrs).playstyles(playstyles).gender(0).preferredFoot("Right").weakFoot(3).skillMoves(3).heightCm(180).roleFamiliarity(Map.of()).build();
    }
}
