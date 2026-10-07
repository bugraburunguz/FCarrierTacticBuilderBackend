package com.fcareer.gameconfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.fitengine.model.Attr;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EaKeyAttributesSeedTest {

    private static final Map<String, String> EA_POSITION_TO_GROUP = Map.ofEntries(
            Map.entry("GK", "GK"), Map.entry("RWB", "FB"), Map.entry("LWB", "FB"), Map.entry("RB", "FB"), Map.entry("LB", "FB"),
            Map.entry("CB", "CB"), Map.entry("CDM", "CDM"), Map.entry("CM", "CM"), Map.entry("CAM", "CAM"), Map.entry("RM", "WM"),
            Map.entry("LM", "WM"), Map.entry("RW", "W"), Map.entry("LW", "W"), Map.entry("CF", "ST"), Map.entry("ST", "ST"));

    private static ObjectMapper mapper;
    private static JsonNode keyAttributes;
    private static JsonNode positionBase;
    private static JsonNode positionBaseline;

    @BeforeAll
    static void load() throws IOException {
        mapper = new ObjectMapper();
        keyAttributes = read("seed/ea-key-attributes.json").path("positions");
        positionBase = read("seed/role-weighting.json").path("positionBase");
        positionBaseline = read("seed/role-calibration-v2.json").path("positionBaseline");
    }

    private static JsonNode read(String path) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return mapper.readTree(in);
        }
    }

    private static double share(JsonNode base, JsonNode attrs) {
        double total = 0;
        for (JsonNode weight : base) {
            total += weight.asDouble();
        }
        double covered = 0;
        for (JsonNode attr : attrs) {
            covered += base.path(attr.asText()).asDouble(0);
        }
        return covered / total;
    }

    @Test
    void everyPositionHasFiveValidAttributes() {
        assertThat(keyAttributes.size()).isEqualTo(17);
        keyAttributes.fields().forEachRemaining(entry -> {
            assertThat(entry.getValue()).hasSize(5);
            entry.getValue().forEach(attr -> assertThat(Attr.fromKey(attr.asText())).as(entry.getKey() + " " + attr).isPresent());
        });
    }

    @Test
    void goalkeeperKeyAttributesAreTheGoalkeeperSet() {
        List<String> keys = new ArrayList<>();
        keyAttributes.path("GK").forEach(a -> keys.add(a.asText()));
        assertThat(keys).containsExactlyInAnyOrder("GKReflexes", "GKHandling", "GKDiving", "GKPositioning", "GKKicking");
    }

    @Test
    void v2BaselineCoversEaKeyAttributesBetterThanV1OnAverage() {
        double v1 = 0;
        double v2 = 0;
        int count = 0;
        for (Map.Entry<String, String> entry : EA_POSITION_TO_GROUP.entrySet()) {
            JsonNode attrs = keyAttributes.path(entry.getKey());
            v1 += share(positionBase.path(entry.getValue()), attrs);
            v2 += share(positionBaseline.path(entry.getValue()), attrs);
            count++;
        }
        assertThat(v2 / count).isGreaterThan(v1 / count);
    }

    @Test
    void bothBaselinesGiveEveryOutfieldGroupAtLeastAThirdOfWeightToEaKeyAttributes() {
        EA_POSITION_TO_GROUP.forEach((position, group) -> {
            JsonNode attrs = keyAttributes.path(position);
            assertThat(share(positionBase.path(group), attrs)).as("v1 " + position).isGreaterThan(0.33);
            assertThat(share(positionBaseline.path(group), attrs)).as("v2 " + position).isGreaterThan(0.33);
        });
    }
}
