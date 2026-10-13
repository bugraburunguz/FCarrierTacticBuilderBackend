package com.fcareer.fitengine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fcareer.fitengine.model.AcceleRate;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.norms.AttrNorms;
import com.fcareer.fitengine.norms.PosDist;
import com.fcareer.fitengine.norms.RoleSeeds;
import com.fcareer.fitengine.service.FitEngineV3;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E3-5 rapor girdisi: golden set oyuncuları + teşhis oyuncuları için v3 currentFit ve ilk 3 katkıyı target/fit-report.json'a yazar.
 * tools/fit-seeds/level-fit-report.mjs bunu fit_level_norms ile birleştirip levelFit ve v1→v3 tablosunu üretir.
 */
class FitReportExportTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final FitEngineV3 ENGINE = new FitEngineV3(new AttrNorms(MAPPER), new PosDist(MAPPER), new RoleSeeds(MAPPER));

    @Test
    void exportsCurrentFitAndTopContributionsForGoldenAndDiagnosisPlayers() throws Exception {
        JsonNode fixture;
        try (InputStream in = new ClassPathResource("golden-set.fc27.json").getInputStream()) {
            fixture = MAPPER.readTree(in);
        }
        ArrayNode out = MAPPER.createArrayNode();
        for (JsonNode block : fixture.path("blocks")) {
            List<String> names = new ArrayList<>();
            block.path("strict").forEach((pair) -> pair.forEach((n) -> names.add(n.asText())));
            block.path("observe").forEach((pair) -> pair.forEach((n) -> names.add(n.asText())));
            for (String name : names.stream().distinct().toList()) {
                FitPlayer player = toPlayer(fixture.path("players").path(name));
                List<String> blockPositions = new ArrayList<>();
                block.path("positions").forEach((p) -> blockPositions.add(p.asText()));
                String position = player.getPositions().stream().filter(blockPositions::contains).findFirst().orElse(blockPositions.get(0));
                out.add(entry("golden", block.path("id").asText(), name, player, position, block.path("role").asText(), block.path("focus").asText(), null));
            }
        }
        for (JsonNode d : fixture.path("diagnosis")) {
            FitPlayer player = toPlayer(fixture.path("players").path(d.path("name").asText()));
            out.add(entry("diagnosis", "", d.path("name").asText(), player, d.path("position").asText(), d.path("role").asText(), d.path("focus").asText(), d.path("v1").asDouble()));
        }
        Path target = Path.of("target", "fit-report.json");
        Files.createDirectories(target.getParent());
        Files.writeString(target, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(out));
        assertThat(out.size()).isGreaterThan(40);
    }

    private static ObjectNode entry(String kind, String block, String name, FitPlayer player, String position, String role, String focus, Double v1) {
        FitEngineV3.Result result = ENGINE.currentFit(player, position, role, focus, Set.of());
        ObjectNode node = MAPPER.createObjectNode();
        node.put("kind", kind).put("block", block).put("name", name).put("ovr", player.getOverall()).put("gender", player.getGender());
        node.put("position", position).put("role", role).put("focus", focus).put("currentFit", result.currentFit());
        if (v1 != null) {
            node.put("v1", v1);
        }
        ArrayNode top = node.putArray("top3");
        result.top(3).forEach((c) -> top.addObject().put("term", c.term()).put("key", c.key()).put("points", Math.round(c.points() * 10.0) / 10.0));
        return node;
    }

    private static FitPlayer toPlayer(JsonNode n) {
        Map<Attr, Integer> attrs = new EnumMap<>(Attr.class);
        n.path("attrs").fields().forEachRemaining((e) -> Attr.fromKey(e.getKey()).ifPresent((a) -> attrs.put(a, e.getValue().asInt())));
        Map<String, Integer> playstyles = new HashMap<>();
        n.path("playstyles").fields().forEachRemaining((e) -> playstyles.put(e.getKey(), e.getValue().asInt()));
        List<String> positions = new ArrayList<>();
        n.path("positions").forEach((p) -> positions.add(p.asText()));
        return FitPlayer.builder().id(n.path("id").asLong()).name(n.path("name").asText()).overall(n.path("ovr").asInt()).positions(positions)
                .attrs(attrs).playstyles(playstyles).acceleRate(AcceleRate.fromLabel(n.path("acceleRate").asText(null)).orElse(null))
                .gender(0).preferredFoot(n.path("foot").isNull() ? null : n.path("foot").asText()).weakFoot(n.path("weakFoot").isNull() ? null : n.path("weakFoot").asInt())
                .skillMoves(n.path("skillMoves").isNull() ? null : n.path("skillMoves").asInt()).heightCm(n.path("heightCm").isNull() ? null : n.path("heightCm").asInt())
                .roleFamiliarity(Map.of()).build();
    }
}
