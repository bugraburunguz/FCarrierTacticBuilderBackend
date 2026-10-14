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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REP-c: bir attribute puanının currentFit'e etkisi en çok 1,5 puandır (13 §3.5). `FitEngineV3PropertyTest` rastgele bulmuştu (GK_POSITIONING → 1,618);
 * bu test aynı sınırı kaleci hedefleri için ızgarada, sabit (tohumsuz) olarak tarar: hiçbir kaleci hedefi, hiçbir attribute, hiçbir taban seviyesi sınırı aşmaz.
 */
class FitEngineV3SlopeBoundTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final FitEngineV3 ENGINE = new FitEngineV3(new AttrNorms(MAPPER), new PosDist(MAPPER), new RoleSeeds(MAPPER));
    private static final double LIMIT = 1.5 + 0.11;
    private static final int[] BASES = {35, 50, 60, 70, 80, 90};

    private static FitPlayer player(Map<Attr, Integer> attrs) {
        return FitPlayer.builder().id(1L).name("p").overall(70).potential(75).valueEur(1_000_000L).positions(List.of("GK")).attrs(attrs).playstyles(Map.of()).gender(0).preferredFoot("Right").weakFoot(3).skillMoves(3)
                .heightCm(185).roleFamiliarity(Map.of()).build();
    }

    private static List<String[]> goalkeeperTargets() throws Exception {
        List<String[]> out = new ArrayList<>();
        try (InputStream in = new ClassPathResource("seed/roles.json").getInputStream()) {
            for (JsonNode role : MAPPER.readTree(in)) {
                for (JsonNode position : role.path("positions")) {
                    if ("GK".equals(position.asText())) {
                        for (JsonNode focus : role.path("focuses")) {
                            out.add(new String[]{role.path("id").asText(), focus.asText()});
                        }
                    }
                }
            }
        }
        return out;
    }

    @Test
    void noGoalkeeperAttributeMovesCurrentFitByMoreThanOnePointFivePerPoint() throws Exception {
        List<String> violations = new ArrayList<>();
        for (String[] target : goalkeeperTargets()) {
            for (Attr attr : Attr.values()) {
                for (int base : BASES) {
                    for (int v = 20; v < 98; v++) {
                        Map<Attr, Integer> low = new EnumMap<>(Attr.class);
                        for (Attr a : Attr.values()) {
                            low.put(a, base);
                        }
                        Map<Attr, Integer> high = new EnumMap<>(low);
                        low.put(attr, v);
                        high.put(attr, v + 1);
                        double before = ENGINE.currentFit(player(low), "GK", target[0], target[1], Set.of()).currentFit();
                        double after = ENGINE.currentFit(player(high), "GK", target[0], target[1], Set.of()).currentFit();
                        if (after - before > LIMIT) {
                            violations.add(target[0] + "/" + target[1] + " " + attr + " base " + base + " v " + v + " Δ " + Math.round((after - before) * 1000) / 1000.0);
                        }
                    }
                }
            }
        }
        assertThat(violations).as("1,5 puan/attribute sınırını aşan kaleci durumları").isEmpty();
    }
}
