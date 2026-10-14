package com.fcareer.development;

import com.fcareer.development.service.GrowthProjector;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E3-6 futureFit(+1 sezon) büyüme modeli. projectedRating tanımı: sezon sonu beklenen OVR = min(POT, OVR + round(kalan boşluk × yaş çarpanı)).
 * Attribute artışı: sezon OVR kazancı × trainability (çevik/hız attribute'ları 0,3×, diğerleri 1,0×), 99'da kırpılır.
 */
class GrowthProjectorTest {

    private final GrowthProjector projector = new GrowthProjector(new GrowthProjector.Config());

    private static FitPlayer player(int age, int ovr, int pot) {
        Map<Attr, Integer> attrs = new EnumMap<>(Attr.class);
        for (Attr a : Attr.values()) {
            attrs.put(a, 60);
        }
        return FitPlayer.builder().id(1L).name("p").age(age).overall(ovr).potential(pot).positions(List.of("ST")).attrs(attrs).playstyles(Map.of()).gender(0).build();
    }

    @Test
    void aYoungPlayerWithHeadroomGrowsAndItIsReflectedInProjectedRatingAndAttributes() {
        GrowthProjector.Projection p = projector.project(player(19, 70, 86));
        assertThat(p.projectedRating()).isBetween(75, 79);
        assertThat(p.grown().getAttrs().get(Attr.FINISHING)).isGreaterThan(60);
        assertThat(p.grown().getAttrs().get(Attr.SPRINT_SPEED)).as("zor gelişen attribute daha az artar").isLessThan(p.grown().getAttrs().get(Attr.FINISHING));
    }

    @Test
    void ageStopsGrowth() {
        GrowthProjector.Projection p = projector.project(player(31, 78, 80));
        assertThat(p.projectedRating()).isEqualTo(78);
        assertThat(p.grown().getAttrs().get(Attr.FINISHING)).isEqualTo(60);
    }

    @Test
    void projectedRatingNeverExceedsPotentialOrNinetyNine() {
        assertThat(projector.project(player(18, 84, 85)).projectedRating()).isLessThanOrEqualTo(85);
        assertThat(projector.project(player(18, 98, 99)).grown().getAttrs().values()).allMatch((v) -> v <= 99);
    }

    @Test
    void anUnknownPotentialOrAgeMeansNoGrowthRatherThanAGuess() {
        FitPlayer noPotential = FitPlayer.builder().id(2L).age(20).overall(70).positions(List.of("ST")).attrs(player(20, 70, 80).getAttrs()).playstyles(Map.of()).build();
        assertThat(projector.project(noPotential).projectedRating()).isEqualTo(70);
    }
}
