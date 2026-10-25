package com.fcareer.advisor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.advisor.service.SideChoice;
import com.fcareer.advisor.service.SideChoice.Best;
import com.fcareer.advisor.service.SideChoice.Choice;
import com.fcareer.advisor.service.SideChoice.RoleAtSides;
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
 * FIT-3 madde 13 (BEHAVIOR CHANGE): kanat mevkilerinde taraf, iki tarafın en iyi rol Güç'üne göre (FitEngineV3 ayak terimleri tarzı taşır:
 * orta açan roller aynı ayak, içe kat edenler ters ayak tarafında bonus; zayıf ayak yükseldikçe fark küçülür). Fark eşik (0,5) altındaysa
 * eski kural: listedeki taraf, sonra ayak. Gerekçe satırı oyuncunun gerçek attribute'larıyla.
 */
class SideChoiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final FitEngineV3 ENGINE = new FitEngineV3(new AttrNorms(MAPPER), new PosDist(MAPPER), new RoleSeeds(MAPPER));
    private static final double THRESHOLD = 0.5;

    private static double foot(FitEngineV3.Result r) {
        return r.contributions().stream().filter((c) -> c.term().equals("foot")).mapToDouble(FitEngineV3.Contribution::points).sum();
    }

    /** İki kanattaki bütün rol/odaklar (gerçek motor). */
    private static List<RoleAtSides> roles(FitPlayer player, String here, String there) {
        List<RoleAtSides> out = new ArrayList<>();
        try (InputStream in = new ClassPathResource("seed/roles.json").getInputStream()) {
            for (JsonNode role : MAPPER.readTree(in)) {
                String positions = role.path("positions").toString();
                if (!positions.contains("\"" + here + "\"") || !positions.contains("\"" + there + "\"")) {
                    continue;
                }
                for (JsonNode focus : role.path("focuses")) {
                    FitEngineV3.Result h = ENGINE.currentFit(player, here, role.path("id").asText(), focus.asText(), Set.of());
                    FitEngineV3.Result t = ENGINE.currentFit(player, there, role.path("id").asText(), focus.asText(), Set.of());
                    out.add(new RoleAtSides(role.path("name").asText() + " (" + focus.asText() + ")", h.currentFit(), foot(h), t.currentFit(), foot(t)));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    private static FitPlayer winger(Map<Attr, Integer> profile, String foot, int weakFoot, List<String> listed) {
        Map<Attr, Integer> attrs = new EnumMap<>(Attr.class);
        for (Attr a : Attr.values()) {
            attrs.put(a, a.name().startsWith("GK") ? 12 : 64);
        }
        attrs.putAll(Map.of(Attr.ACCELERATION, 80, Attr.SPRINT_SPEED, 80, Attr.DRIBBLING, 76, Attr.BALL_CONTROL, 75, Attr.AGILITY, 78));
        attrs.putAll(profile);
        return FitPlayer.builder().id(1L).name("Kanat").overall(72).potential(80).positions(listed).attrs(attrs).playstyles(Map.of()).gender(0)
                .preferredFoot(foot).weakFoot(weakFoot).skillMoves(3).heightCm(176).roleFamiliarity(Map.of()).build();
    }

    private static final Map<Attr, Integer> CROSSER = Map.of(Attr.CROSSING, 80, Attr.CURVE, 76, Attr.FINISHING, 55, Attr.LONG_SHOTS, 58, Attr.POSITIONING, 62);
    private static final Map<Attr, Integer> FINISHER = Map.of(Attr.CROSSING, 56, Attr.CURVE, 58, Attr.FINISHING, 80, Attr.LONG_SHOTS, 74, Attr.POSITIONING, 78);

    private static Choice choose(FitPlayer p, String picked) {
        // Ayna mevki mevki dışı sayılmaz (bothSides); vektör asimetrisi ayaksız ortalamayla nötr (sideBests); fark yalnız ayak/tarz.
        String mirror = SideChoice.mirror(picked);
        Map<String, Best> sides = SideChoice.sideBests(picked, mirror, roles(SideChoice.bothSides(p, picked), picked, mirror));
        return SideChoice.choose(picked, sides, p, THRESHOLD);
    }

    @Test
    void rightFootedCrosserGoesRightAsAWinger() {
        Choice c = choose(winger(CROSSER, "Right", 2, List.of("LW")), "LW");
        assertThat(c.position()).isEqualTo("RW");
        assertThat(c.roleName()).startsWith("Winger");
        assertThat(c.reason()).isEqualTo("Orta profili baskın (Orta 78 · Bitiricilik 58) + sağ ayak → RW · Winger");
    }

    @Test
    void rightFootedFinisherGoesLeftAsAnInsideForward() {
        Choice c = choose(winger(FINISHER, "Right", 2, List.of("RW")), "RW");
        assertThat(c.position()).isEqualTo("LW");
        assertThat(c.roleName()).startsWith("Inside Forward");
        assertThat(c.reason()).startsWith("Bitirici profil baskın (Orta 57 · Bitiricilik 77) + sağ ayak → LW · Inside Forward");
    }

    @Test
    void fiveStarWeakFootKeepsTheListedSide() {
        Choice c = choose(winger(CROSSER, "Right", 5, List.of("LW")), "LW");
        assertThat(c.position()).as("ayak bonusu yok → iki taraf eşit → eski kural").isEqualTo("LW");
        assertThat(c.styled()).isFalse();
    }

    @Test
    void tieBelowThresholdKeepsTheOldPick() {
        FitPlayer p = winger(CROSSER, "Right", 2, List.of("LW"));
        Choice c = SideChoice.choose("LW", Map.of("LW", new Best("Winger (Attack)", 70.2), "RW", new Best("Winger (Attack)", 70.5)), p, THRESHOLD);
        assertThat(c.position()).isEqualTo("LW");
        assertThat(c.styled()).isFalse();
        assertThat(c.reason()).isNull();
    }

    @Test
    void nonWidePositionsAreUntouched() {
        Choice c = SideChoice.choose("CM", Map.of(), winger(CROSSER, "Right", 2, List.of("CM")), THRESHOLD);
        assertThat(c.position()).isEqualTo("CM");
        assertThat(c.reason()).isNull();
    }
}
