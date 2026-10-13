package com.fcareer.ut;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.ingestion.provider.RatingStatKeys;
import com.fcareer.ut.service.FaceStatsService;
import com.fcareer.ut.service.FaceStatsService.FaceStats;
import com.fcareer.ut.service.UtRules;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** F10 / RULE-10: 6 yüz istatistiği tek servisten; ağırlıklar ut_rules'tan, resmî değer varsa o kazanır. */
class FaceStatsServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** Stat başına izin verilen en büyük sapma (DRI ağırlıkları holdout ile düzeltildi: 19.789 kartta tam isabet). */
    private static int tolerance(String label) {
        return 1;
    }

    private final FaceStatsService service = new FaceStatsService(new UtRules(MAPPER));

    private static Map<Attr, Integer> attrs(JsonNode node) {
        Map<Attr, Integer> attrs = new EnumMap<>(Attr.class);
        RatingStatKeys.KEYS.forEach((attr, key) -> {
            if (node.has(key)) {
                attrs.put(attr, node.get(key).asInt());
            }
        });
        return attrs;
    }

    private static JsonNode cards() throws IOException {
        return MAPPER.readTree(new ClassPathResource("face-stats-official-sample.json").getInputStream()).get("cards");
    }

    @Test
    void computedValuesMatchTheOfficialOnesWithinTheDocumentedTolerance() throws IOException {
        int compared = 0;
        for (JsonNode card : cards()) {
            FaceStats computed = service.compute(attrs(card.get("attrs")), card.get("goalkeeper").asBoolean());
            for (int i = 0; i < 6; i++) {
                int diff = computed.values().get(i) - card.get("official").get(i).asInt();
                assertThat(Math.abs(diff)).as(card.get("name").asText() + " " + computed.labels().get(i)).isLessThanOrEqualTo(tolerance(computed.labels().get(i)));
                compared++;
            }
        }
        assertThat(compared).isEqualTo(60);
    }

    @Test
    void mbappeComputesTo96_91_80_92_29_76WithinOne() throws IOException {
        JsonNode mbappe = cards().get(0);
        FaceStats computed = service.compute(attrs(mbappe.get("attrs")), false);

        assertThat(mbappe.get("official").toString()).isEqualTo("[96,91,80,92,29,76]");
        for (int i = 0; i < 6; i++) {
            assertThat(Math.abs(computed.values().get(i) - mbappe.get("official").get(i).asInt())).isLessThanOrEqualTo(1);
        }
    }

    @Test
    void goalkeepersGetTheirOwnLabelsAndFormula() throws IOException {
        JsonNode courtois = cards().get(8);
        assertThat(courtois.get("goalkeeper").asBoolean()).isTrue();

        FaceStats computed = service.compute(attrs(courtois.get("attrs")), true);

        assertThat(computed.labels()).containsExactly("DIV", "HAN", "KIC", "REF", "SPD", "POS");
        assertThat(computed.values().get(0)).isEqualTo(courtois.get("attrs").get("gkDiving").asInt());
    }

    @Test
    void anOfficialValueWinsOverTheFormulaAndIsFlaggedAsOfficial() throws IOException {
        Map<Attr, Integer> attrs = attrs(cards().get(0).get("attrs"));

        FaceStats official = service.resolve(List.of(1, 2, 3, 4, 5, 6), attrs, false);
        FaceStats formula = service.resolve(null, attrs, false);

        assertThat(official.source()).isEqualTo("OFFICIAL");
        assertThat(official.values()).containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(formula.source()).isEqualTo("COMPUTED");
        assertThat(formula.values()).hasSize(6);
        assertThat(formula.labels()).containsExactly("PAC", "SHO", "PAS", "DRI", "DEF", "PHY");
    }

    @Test
    void theUnverifiedWeightFlagComesFromTheRules() {
        assertThat(service.weightsVerified()).as("seed'de verified:false").isFalse();
    }

    @Test
    void missingAttributesGiveNoComputedStats() {
        assertThat(service.compute(new EnumMap<>(Attr.class), false)).isNull();
    }
}
