package com.fcareer.fitengine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.fitengine.model.AcceleRate;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.service.FitEngineV3;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Altın set (golden-set.fc27.json) değerlendirmesi: verilen motorla kesin çift kırıkları ve rol bazında dağılım (E3-4b aday ölçümü). */
final class GoldenSupport {

    record Result(int strictTotal, List<String> strictFailures, List<String> calibrationFailures) {
    }

    private GoldenSupport() {
    }

    static Result evaluate(FitEngineV3 engine, ObjectMapper mapper, String onlyRole) throws Exception {
        JsonNode fixture;
        try (InputStream in = new ClassPathResource("golden-set.fc27.json").getInputStream()) {
            fixture = mapper.readTree(in);
        }
        Map<String, FitPlayer> players = new HashMap<>();
        fixture.path("players").fields().forEachRemaining((e) -> players.put(e.getKey(), toPlayer(e.getValue())));
        int total = 0;
        List<String> failures = new ArrayList<>();
        List<String> calibration = new ArrayList<>();
        for (JsonNode block : fixture.path("blocks")) {
            if (onlyRole != null && !onlyRole.equals(block.path("role").asText())) {
                continue;
            }
            for (JsonNode pair : block.path("strict")) {
                total++;
                if (!(fit(engine, players, block, pair.get(0).asText()) > fit(engine, players, block, pair.get(1).asText()))) {
                    failures.add(block.path("label").asText() + ": " + pair.get(0).asText() + " > " + pair.get(1).asText());
                }
            }
            for (JsonNode pair : block.path("calibration")) {
                if (!(fit(engine, players, block, pair.get(0).asText()) > fit(engine, players, block, pair.get(1).asText()))) {
                    calibration.add(block.path("label").asText() + ": " + pair.get(0).asText() + " > " + pair.get(1).asText());
                }
            }
        }
        return new Result(total, failures, calibration);
    }

    private static double fit(FitEngineV3 engine, Map<String, FitPlayer> players, JsonNode block, String name) {
        FitPlayer player = players.get(name);
        List<String> blockPositions = new ArrayList<>();
        block.path("positions").forEach((p) -> blockPositions.add(p.asText()));
        String position = player.getPositions().stream().filter(blockPositions::contains).findFirst().orElse(blockPositions.get(0));
        return engine.currentFit(player, position, block.path("role").asText(), block.path("focus").asText(), Set.of()).currentFit();
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
