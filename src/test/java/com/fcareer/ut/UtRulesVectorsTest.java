package com.fcareer.ut;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.ut.model.SbcModels.Rarity;
import com.fcareer.ut.service.ChemistryCalculator;
import com.fcareer.ut.service.ChemistryCalculator.ChemCard;
import com.fcareer.ut.service.ChemistryCalculator.ChemManager;
import com.fcareer.ut.service.ChemistryCalculator.ChemResult;
import com.fcareer.ut.service.ChemistryCalculator.ChemSlot;
import com.fcareer.ut.service.SbcSolverService;
import com.fcareer.ut.service.SquadRatingCalculator;
import com.fcareer.ut.service.UtRules;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Backend ve web aynı vektör dosyasını (seed/ut-rules.fc27.vectors.json) çalıştırır; web kopyası src/lib/utRules.fc27.vectors.json. */
class UtRulesVectorsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static JsonNode vectors;
    private static UtRules rules;
    private static SbcSolverService sbc;

    @BeforeAll
    static void load() throws Exception {
        vectors = MAPPER.readTree(new ClassPathResource("seed/ut-rules.fc27.vectors.json").getInputStream());
        rules = new UtRules(MAPPER);
        sbc = new SbcSolverService(MAPPER);
        Method load = SbcSolverService.class.getDeclaredMethod("load");
        load.setAccessible(true);
        load.invoke(sbc);
    }

    @Test
    void squadRating() {
        for (JsonNode v : vectors.get("squadRating")) {
            List<Integer> ratings = new ArrayList<>();
            v.get("ratings").forEach(n -> ratings.add(n.asInt()));
            assertThat(SquadRatingCalculator.squadRating(ratings, v.get("size").asInt())).as(v.get("name").asText()).isEqualTo(v.get("expected").asInt());
        }
        assertThat(rules.squadSize()).isEqualTo(18);
        assertThat(rules.sbcSize()).isEqualTo(11);
    }

    @Test
    void chemistry() {
        ChemistryCalculator calculator = new ChemistryCalculator(rules);
        for (JsonNode v : vectors.get("chemistry")) {
            List<ChemSlot> slots = new ArrayList<>();
            v.get("xi").forEach(entry -> slots.add(new ChemSlot(entry.get("slot").asText(), card(entry))));
            JsonNode m = v.get("manager");
            ChemManager manager = m == null ? null : new ChemManager(m.get("nationId").asLong(), m.get("leagueId").asLong());
            ChemResult result = calculator.compute(slots, manager);
            List<Integer> expected = new ArrayList<>();
            v.at("/expect/perSlot").forEach(n -> expected.add(n.asInt()));
            assertThat(result.perSlot()).as(v.get("name").asText()).isEqualTo(expected);
            assertThat(result.total()).as(v.get("name").asText() + " toplam").isEqualTo(v.at("/expect/total").asInt());
        }
    }

    private static ChemCard card(JsonNode entry) {
        if (!entry.has("clubId")) {
            return null;
        }
        List<String> positions = new ArrayList<>();
        entry.get("positions").forEach(p -> positions.add(p.asText()));
        return new ChemCard(entry.get("clubId").asLong(), entry.get("leagueId").asLong(), entry.get("nationId").asLong(), entry.path("gender").asInt(0),
                entry.has("type") ? entry.get("type").asText() : null, positions);
    }

    @Test
    void sbcScoreAndMonotonicFallback() {
        for (JsonNode v : vectors.get("sbcScore")) {
            int score = sbc.scoreOf(v.get("ovr").asInt(), Rarity.valueOf(v.get("rarity").asText()));
            String name = v.get("rarity").asText() + " " + v.get("ovr").asInt();
            if (v.has("expect")) {
                assertThat(score).as(name).isEqualTo(v.get("expect").asInt());
            } else {
                assertThat(score).as(name).isGreaterThanOrEqualTo(v.get("expectAtLeast").asInt());
            }
        }
        for (Rarity rarity : Rarity.values()) {
            int previous = 0;
            for (int rating = 45; rating <= 99; rating++) {
                int score = sbc.scoreOf(rating, rarity);
                assertThat(score).as(rarity + " " + rating + " rating artınca skor azalmaz").isGreaterThanOrEqualTo(previous);
                previous = score;
            }
        }
    }

    @Test
    void sbcScoreSeedAgreesWithTheRulesDocument() {
        JsonNode table = rules.document().get("sbcScore");
        for (String kind : List.of("regular", "totw", "hero", "icon")) {
            table.get(kind).fields().forEachRemaining(e -> {
                if (e.getKey().matches("\\d+")) {
                    assertThat(sbc.scoreOf(Integer.parseInt(e.getKey()), Rarity.valueOf(kind.toUpperCase(java.util.Locale.ROOT)))).as(kind + " " + e.getKey()).isEqualTo(e.getValue().asInt());
                }
            });
        }
    }

    @Test
    void qualityAndDefensiveApproach() {
        for (JsonNode v : vectors.get("quality")) {
            assertThat(rules.qualityOf(v.get("ovr").asInt())).isEqualTo(v.get("expect").asText());
        }
        for (JsonNode v : vectors.get("defensiveApproach")) {
            assertThat(rules.defensiveApproach(v.get("lineHeight").asInt())).as("hat " + v.get("lineHeight")).isEqualTo(v.get("expect").asText());
        }
    }
}
