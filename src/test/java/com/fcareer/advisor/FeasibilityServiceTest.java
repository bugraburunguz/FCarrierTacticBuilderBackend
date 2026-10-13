package com.fcareer.advisor;

import com.fcareer.advisor.configuration.RecommenderProperties;
import com.fcareer.advisor.service.FeasibilityService;
import com.fcareer.advisor.service.FeasibilityService.Feasibility;
import com.fcareer.advisor.service.FeasibilityService.SquadProfile;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.tactics.model.FormationDefinition;
import com.fcareer.tactics.model.FormationSlot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FeasibilityServiceTest {

    private final FeasibilityService service = new FeasibilityService(new RecommenderProperties());

    private FitPlayer player(long id, String position, int stamina, int pace) {
        Map<Attr, Integer> attrs = new EnumMap<>(Attr.class);
        attrs.put(Attr.STAMINA, stamina);
        attrs.put(Attr.ACCELERATION, pace);
        attrs.put(Attr.SPRINT_SPEED, pace);
        attrs.put(Attr.CROSSING, 75);
        return FitPlayer.builder().id(id).name("P" + id).overall(80).positions(List.of(position)).attrs(attrs)
                .playstyles(Map.of()).build();
    }

    private FormationDefinition formation(String id, String... positions) {
        List<FormationSlot> slots = new ArrayList<>();
        for (int i = 0; i < positions.length; i++) {
            slots.add(new FormationSlot("S" + i, positions[i], positions[i], "x", 50, 50));
        }
        return new FormationDefinition(id, id, null, null, null, null, null, null, slots);
    }

    private SquadProfile profile(List<FitPlayer> players, Map<Long, Map<String, Double>> scores) {
        return service.profile(players, scores);
    }

    @Test
    void singleStrikerSquadPenalisesTwoStrikerShapesButNotOneStrikerShapes() {
        List<FitPlayer> players = List.of(player(1, "ST", 80, 80), player(2, "LW", 80, 80), player(3, "RW", 80, 80), player(4, "CB", 80, 80));
        Map<Long, Map<String, Double>> scores = new HashMap<>();
        scores.put(1L, Map.of("ST", 85.0));
        scores.put(2L, Map.of("LW", 85.0, "LM", 80.0));
        scores.put(3L, Map.of("RW", 85.0, "RM", 80.0));
        scores.put(4L, Map.of("CB", 85.0));
        SquadProfile profile = profile(players, scores);

        Feasibility fourFourTwo = service.evaluate(players, profile, formation("4-4-2", "ST", "ST", "CB"), "park_the_bus");
        Feasibility fourThreeThree = service.evaluate(players, profile, formation("4-3-3", "ST", "LW", "RW", "CB"), "park_the_bus");

        assertThat(fourFourTwo.multiplier()).isLessThan(0.7);
        assertThat(fourFourTwo.notes()).anyMatch(n -> n.contains("forvet"));
        assertThat(fourThreeThree.multiplier()).isEqualTo(1.0);
    }

    @Test
    void gegenpressNeedsStaminaAndPaceWhileParkTheBusDoesNot() {
        List<FitPlayer> tired = List.of(player(1, "ST", 50, 60), player(2, "CB", 50, 60), player(3, "CM", 50, 60));
        Map<Long, Map<String, Double>> scores = Map.of(1L, Map.of("ST", 80.0), 2L, Map.of("CB", 80.0), 3L, Map.of("CM", 80.0));
        SquadProfile profile = profile(tired, scores);
        FormationDefinition shape = formation("4-3-3", "ST", "CB", "CM");

        double gegen = service.evaluate(tired, profile, shape, "gegenpress").multiplier();
        double bus = service.evaluate(tired, profile, shape, "park_the_bus").multiplier();

        assertThat(gegen).isLessThan(bus);
        assertThat(bus).isEqualTo(1.0);
        assertThat(gegen).isGreaterThanOrEqualTo(0.15);
    }
}
