package com.fcareer.fitengine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E3-5 altın set (docs/review/v2/golden_set.md KESİN sürüm): kesin çiftler %100 ve Kendall τ ≥ 0,8 (yalnız kesin çiftlerde) build'i kırar;
 * gözlem çiftleri yalnız raporlanır. Oyuncular kendi doğal mevkilerinde koşar.
 */
class GoldenSetTest {

    private static final double MIN_TAU = 0.8;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final FitEngineV3 ENGINE = new FitEngineV3(new AttrNorms(MAPPER), new PosDist(MAPPER), new RoleSeeds(MAPPER));

    private final Map<String, FitPlayer> players = new HashMap<>();
    private final JsonNode fixture = load();

    private JsonNode load() {
        try (InputStream in = new ClassPathResource("golden-set.fc27.json").getInputStream()) {
            JsonNode root = MAPPER.readTree(in);
            root.path("players").fields().forEachRemaining((e) -> players.put(e.getKey(), toPlayer(e.getValue())));
            return root;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
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

    private double fit(JsonNode block, String name) {
        FitPlayer player = players.get(name);
        List<String> blockPositions = new ArrayList<>();
        block.path("positions").forEach((p) -> blockPositions.add(p.asText()));
        String position = player.getPositions().stream().filter(blockPositions::contains).findFirst().orElse(blockPositions.get(0));
        return ENGINE.currentFit(player, position, block.path("role").asText(), block.path("focus").asText(), Set.of()).currentFit();
    }

    @Test
    void strictPairsAreOrderedCorrectlyAndKendallTauIsAtLeastPointEight() {
        List<String> failures = new ArrayList<>();
        int concordant = 0;
        int total = 0;
        for (JsonNode block : fixture.path("blocks")) {
            for (JsonNode pair : block.path("strict")) {
                double high = fit(block, pair.get(0).asText());
                double low = fit(block, pair.get(1).asText());
                total++;
                if (high > low) {
                    concordant++;
                } else {
                    failures.add(block.path("label").asText() + ": " + pair.get(0).asText() + " (" + high + ") > " + pair.get(1).asText() + " (" + low + ")");
                }
            }
        }
        double tau = total == 0 ? 0 : (2.0 * concordant - total) / total;
        System.out.println("GOLDEN strict " + concordant + "/" + total + " tau=" + String.format("%.3f", tau));
        assertThat(failures).as("kesin çiftler %100").isEmpty();
        assertThat(tau).isGreaterThanOrEqualTo(MIN_TAU);
    }

    @Test
    void observationPairsAreReportedButNeverFailTheBuild() {
        for (JsonNode block : fixture.path("blocks")) {
            for (JsonNode pair : block.path("observe")) {
                double a = fit(block, pair.get(0).asText());
                double b = fit(block, pair.get(1).asText());
                System.out.println("GOLDEN observe " + block.path("label").asText() + ": " + pair.get(0).asText() + " " + a + (a > b ? " > " : a < b ? " < " : " = ") + pair.get(1).asText() + " " + b);
            }
        }
    }

    @Test
    void goalkeeperLadderIsPreservedInCurrentFit() {
        JsonNode gk = null;
        for (JsonNode block : fixture.path("blocks")) {
            if ("GK".equals(block.path("id").asText())) {
                gk = block;
            }
        }
        List<String> ladder = List.of("Marco Carnesecchi", "Colin Coosemans", "Simon Ngapandouetnbu", "Mikel Santos", "Owen Jochmans");
        for (int i = 0; i + 1 < ladder.size(); i++) {
            assertThat(fit(gk, ladder.get(i))).as(ladder.get(i) + " > " + ladder.get(i + 1)).isGreaterThan(fit(gk, ladder.get(i + 1)));
        }
    }
}
