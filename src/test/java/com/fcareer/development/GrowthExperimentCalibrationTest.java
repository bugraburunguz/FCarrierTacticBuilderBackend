package com.fcareer.development;

import com.fcareer.development.service.GrowthProjector;
import com.fcareer.development.service.GrowthProjector.Stage;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FCA-20 (verified:false, kaynak: Beşiktaş gelişim deneyi 2026-08-01 → 2027-03-01, Efsanevi, 39 oyuncu): sezon kazancı potansiyele kalan farkla
 * orantılı değil, yaşa göre mutlak puan; POT'a kalan fark ve aşama tavanıyla sınırlı. Oynamayan genç altyapıda A takımdan yavaş gelişmez
 * (ALT-1: Procházka altyapıda +4, Gürcebe A takımda +3), bu yüzden iki aşamanın tavanı aynı.
 */
class GrowthExperimentCalibrationTest {

    private final GrowthProjector growth = new GrowthProjector(new GrowthProjector.Config());

    private static FitPlayer player(int age, int ovr, int pot) {
        Map<Attr, Integer> attrs = new EnumMap<>(Attr.class);
        for (Attr a : Attr.values()) {
            attrs.put(a, 55);
        }
        return FitPlayer.builder().id(1L).name("Deney").age(age).overall(ovr).potential(pot).positions(List.of("CM")).attrs(attrs).playstyles(Map.of())
                .roleFamiliarity(Map.of()).gender(0).build();
    }

    private int gain(int age, int ovr, int pot, Stage stage) {
        return growth.project(player(age, ovr, pot), stage).projectedRating() - ovr;
    }

    @Test
    void aLargeHeadroomNoLongerMeansALargeGain() {
        assertThat(gain(20, 58, 76, Stage.FIRST_TEAM)).as("Emir Yaşar: deneyde 7 ayda +2, eski model +8").isEqualTo(3);
        assertThat(gain(19, 60, 80, Stage.FIRST_TEAM)).as("Bircan: deneyde +1, eski model +8").isEqualTo(3);
        assertThat(gain(22, 69, 81, Stage.FIRST_TEAM)).as("Akdağ: deneyde +2, eski model +4").isEqualTo(3);
    }

    @Test
    void theYoungestGrowFastestAndTheAcademyIsNotSlower() {
        assertThat(gain(15, 51, 75, Stage.ACADEMY)).as("Procházka altyapıda: deneyde 7 ayda +4 (yıllık ~+7; sezon devri verisiyle yeniden bakılacak)").isEqualTo(5);
        assertThat(gain(16, 52, 73, Stage.FIRST_TEAM)).as("Gürcebe A takımda, oynamadan: deneyde 7 ayda +3 (yıllık ~+5)").isEqualTo(5);
        assertThat(growth.config().getAcademyMaxGain()).isEqualTo(growth.config().getFirstTeamMaxGain());
    }

    @Test
    void establishedPlayersGrowALittleAndNeverPastPotential() {
        assertThat(gain(26, 81, 84, Stage.FIRST_TEAM)).as("Vlahović: deneyde +2").isEqualTo(2);
        assertThat(gain(25, 82, 87, Stage.FIRST_TEAM)).as("Kökçü: deneyde +1").isEqualTo(2);
        assertThat(gain(28, 77, 78, Stage.FIRST_TEAM)).as("Černý: deneyde +1").isEqualTo(1);
        assertThat(gain(17, 57, 61, Stage.ACADEMY)).as("POT'a 4 kaldıysa en çok +4").isEqualTo(4);
        assertThat(gain(29, 80, 80, Stage.FIRST_TEAM)).isZero();
    }
}
