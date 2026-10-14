package com.fcareer.fitengine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.norms.AttrNorms;
import com.fcareer.fitengine.norms.PosDist;
import com.fcareer.fitengine.norms.RoleSeeds;
import com.fcareer.fitengine.service.FitEngineV3;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** E3-5 özellik testleri (13 §3.5): rastgele oyuncu/rol üzerinde motorun değişmezleri. */
class FitEngineV3PropertyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final RoleSeeds SEEDS = new RoleSeeds(MAPPER);
    private static final FitEngineV3 ENGINE = new FitEngineV3(new AttrNorms(MAPPER), new PosDist(MAPPER), SEEDS);
    private static final double MAX_POINTS_PER_ATTR_POINT = 1.5;
    private static final List<Target> TARGETS = targets();

    record Target(String position, String role, String focus) {
    }

    record Case(Target target, Map<Attr, Integer> attrs, Attr bumped) {
    }

    private static List<Target> targets() {
        List<Target> list = new ArrayList<>();
        try (InputStream in = new ClassPathResource("seed/roles.json").getInputStream()) {
            for (JsonNode role : MAPPER.readTree(in)) {
                for (JsonNode position : role.path("positions")) {
                    for (JsonNode focus : role.path("focuses")) {
                        list.add(new Target(position.asText(), role.path("id").asText(), focus.asText()));
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return list;
    }

    private static FitPlayer player(Target target, Map<Attr, Integer> attrs, boolean inPosition, String foot, int ovr, int potential, Map<String, Integer> playstyles) {
        return FitPlayer.builder().id(1L).name("p").overall(ovr).potential(potential).valueEur(1_000_000L).positions(inPosition ? List.of(target.position()) : List.of("CM"))
                .attrs(attrs).playstyles(playstyles).gender(0).preferredFoot(foot).weakFoot(3).skillMoves(3).heightCm(180).roleFamiliarity(Map.of()).build();
    }

    private static double fit(FitPlayer player, Target target) {
        return ENGINE.currentFit(player, target.position(), target.role(), target.focus(), Set.of()).currentFit();
    }

    @Provide
    Arbitrary<Case> cases() {
        Arbitrary<Target> target = Arbitraries.of(TARGETS);
        Arbitrary<Map<Attr, Integer>> attrs = Arbitraries.integers().between(20, 94).list().ofSize(Attr.values().length).map((values) -> {
            Map<Attr, Integer> map = new EnumMap<>(Attr.class);
            for (int i = 0; i < values.size(); i++) {
                map.put(Attr.values()[i], values.get(i));
            }
            return map;
        });
        return Combinators.combine(target, attrs, Arbitraries.of(Attr.values())).as(Case::new);
    }

    @Property(tries = 400, seed = "20261014")
    void raisingAnAttributeNeverLowersCurrentFitAndMovesItByAtMostOnePointFivePerPoint(@ForAll("cases") Case c) {
        Map<Attr, Integer> raised = new EnumMap<>(c.attrs());
        raised.put(c.bumped(), c.attrs().get(c.bumped()) + 1);
        double before = fit(player(c.target(), c.attrs(), true, "Right", 70, 75, Map.of()), c.target());
        double after = fit(player(c.target(), raised, true, "Right", 70, 75, Map.of()), c.target());
        assertThat(after).as(c.target() + " " + c.bumped()).isGreaterThanOrEqualTo(before - 0.11);
        assertThat(after - before).as(c.target() + " " + c.bumped()).isLessThanOrEqualTo(MAX_POINTS_PER_ATTR_POINT + 0.11);
    }

    @Property(tries = 200)
    void overallPotentialAndValueNeverChangeCurrentFit(@ForAll("cases") Case c) {
        double low = fit(player(c.target(), c.attrs(), true, "Right", 50, 55, Map.of()), c.target());
        double high = fit(player(c.target(), c.attrs(), true, "Right", 90, 95, Map.of()), c.target());
        assertThat(high).isEqualTo(low);
    }

    @Property(tries = 200)
    void playstyleBonusIsBoundedByTheEightPointCap(@ForAll("cases") Case c) {
        Map<String, Integer> every = new HashMap<>();
        SEEDS.playstyles(c.target().role()).forEach((ps) -> every.put(ps.playstyle(), 2));
        double without = fit(player(c.target(), c.attrs(), true, "Right", 70, 75, Map.of()), c.target());
        double with = fit(player(c.target(), c.attrs(), true, "Right", 70, 75, every), c.target());
        assertThat(with - without).isBetween(-0.11, SEEDS.modifiers().path("psCap").path("maxTotal").asDouble() + 0.11);
    }

    @Property(tries = 300)
    void playingOutOfPositionNeverBeatsPlayingInPosition(@ForAll("cases") Case c) {
        double in = fit(player(c.target(), c.attrs(), true, "Right", 70, 75, Map.of()), c.target());
        double out = fit(player(c.target(), c.attrs(), false, "Right", 70, 75, Map.of()), c.target());
        assertThat(out).as(c.target().toString()).isLessThanOrEqualTo(in + 0.11);
    }

    @Property(tries = 300)
    void theOppositeFootOnTheWingNeverLowersAnInsideRoleAndTheSameFootNeverLowersACrossingRole(@ForAll("cases") Case c) {
        String position = c.target().position();
        if (!List.of("LW", "RW", "LM", "RM").contains(position)) {
            return;
        }
        String natural = position.startsWith("L") ? "Left" : "Right";
        String opposite = natural.equals("Left") ? "Right" : "Left";
        double same = fit(player(c.target(), c.attrs(), true, natural, 70, 75, Map.of()), c.target());
        double cut = fit(player(c.target(), c.attrs(), true, opposite, 70, 75, Map.of()), c.target());
        JsonNode foot = SEEDS.modifiers().path("foot");
        boolean inside = foot.path("cutInside").path("roles").toString().contains("\"" + c.target().role() + "\"");
        boolean crossing = foot.path("crossing").path("roles").toString().contains("\"" + c.target().role() + "\"");
        if (inside) {
            assertThat(cut).isGreaterThanOrEqualTo(same - 0.11);
        }
        if (crossing && !inside) {
            assertThat(same).isGreaterThanOrEqualTo(cut - 0.11);
        }
    }

    @Provide
    Arbitrary<Case> strongCases() {
        Arbitrary<Target> target = Arbitraries.of(TARGETS);
        Arbitrary<Map<Attr, Integer>> attrs = Arbitraries.integers().between(86, 97).list().ofSize(Attr.values().length).map((values) -> {
            Map<Attr, Integer> map = new EnumMap<>(Attr.class);
            for (int i = 0; i < values.size(); i++) {
                map.put(Attr.values()[i], values.get(i));
            }
            return map;
        });
        return Combinators.combine(target, attrs, Arbitraries.of(Attr.values())).as(Case::new);
    }

    @Property(tries = 400, seed = "20261014")
    void atTheTopEndTwoDifferentInputsNeverCollapseToTheSameCurrentFit(@ForAll("strongCases") Case c) {
        Double weight = SEEDS.vector(c.target().position(), c.target().role(), c.target().focus()).orElseThrow().get(c.bumped());
        if (weight == null || weight < 0.02) {
            return;
        }
        Map<Attr, Integer> raised = new EnumMap<>(Attr.class);
        raised.putAll(c.attrs());
        raised.put(c.bumped(), c.attrs().get(c.bumped()) + 1);
        double before = fit(player(c.target(), c.attrs(), true, "Right", 70, 75, Map.of()), c.target());
        double after = fit(player(c.target(), raised, true, "Right", 70, 75, Map.of()), c.target());
        assertThat(after).as(c.target() + " " + c.bumped()).isGreaterThan(before);
        assertThat(after).isLessThan(100.0);
    }
}
