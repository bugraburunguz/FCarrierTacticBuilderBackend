package com.fcareer.tacticcode.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.tacticcode.model.EaBuildUp;
import com.fcareer.tacticcode.model.EaCodecData;
import com.fcareer.tacticcode.model.EaTactic;
import com.fcareer.tacticcode.model.EaTactic.EaSlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 64 adet gerçek FC27 taktik kodu (EasySBC taktik merkezi, oyunda çalıştığı bilinen kodlar) ile codec'in
 * hem encode hem decode yönünde birebir olduğunu doğrular. Vektörler: src/test/resources/tactic-code/easysbc-vectors.json
 */
class EaTacticCodecEasySbcVectorsTest {

    private static final String VECTORS = "tactic-code/easysbc-vectors.json";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private EaTacticCodec codec;
    private JsonNode vectors;

    @BeforeEach
    void setUp() throws IOException {
        codec = new EaTacticCodec(objectMapper);
        codec.load();
        try (InputStream in = new ClassPathResource(VECTORS).getInputStream()) {
            vectors = objectMapper.readTree(in).path("vectors");
        }
    }

    @Test
    void encodesEveryPublishedTacticBitExact() {
        List<String> failures = new ArrayList<>();
        for (JsonNode v : vectors) {
            String expected = v.path("code").asText();
            String actual = codec.encode(toTactic(v));
            if (!expected.equals(actual)) {
                failures.add(v.path("formationId").asText() + " expected " + expected + " got " + actual);
            }
        }
        assertThat(failures).isEmpty();
        assertThat(vectors.size()).isEqualTo(64);
    }

    @Test
    void decodesEveryPublishedTacticBackToTheSameRolesAndFocus() {
        for (JsonNode v : vectors) {
            EaTactic decoded = codec.decode(v.path("code").asText()).orElseThrow();
            EaTactic expected = toTactic(v);
            assertThat(decoded.formationId()).as(v.path("code").asText()).isEqualTo(expected.formationId());
            assertThat(decoded.lineHeight()).as(v.path("code").asText()).isEqualTo(expected.lineHeight());
            assertThat(decoded.buildUp()).as(v.path("code").asText()).isEqualTo(expected.buildUp());
            assertThat(decoded.slots()).as(v.path("code").asText()).containsExactlyElementsOf(expected.slots());
        }
    }

    private EaTactic toTactic(JsonNode v) {
        List<EaSlot> slots = new ArrayList<>();
        for (JsonNode r : v.path("roles")) {
            int roleId = r.path("roleId").asInt();
            EaCodecData.Role role = codec.role(roleId).orElseThrow();
            int focusIndex = role.focuses().indexOf(r.path("focus").asText());
            assertThat(focusIndex).as("focus " + r + " for role " + role.name()).isGreaterThanOrEqualTo(0);
            slots.add(new EaSlot(roleId, focusIndex));
        }
        return new EaTactic(v.path("formationId").asText(), buildUp(v.path("buildUp").asText()), v.path("lineHeight").asInt(), slots);
    }

    private static EaBuildUp buildUp(String text) {
        return switch (text) {
            case "Short Passing" -> EaBuildUp.SHORT_PASSING;
            case "Counter" -> EaBuildUp.COUNTER;
            default -> EaBuildUp.BALANCED;
        };
    }
}
