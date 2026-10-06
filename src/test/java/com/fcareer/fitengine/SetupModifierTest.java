package com.fcareer.fitengine;

import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.IntentProfile;
import com.fcareer.fitengine.model.TeamSetup;
import com.fcareer.fitengine.service.SetupModifier;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SetupModifierTest {

    private final SetupModifier modifier = new SetupModifier();

    private FitPlayer player(int pace, int passing, int air, int awareness) {
        Map<Attr, Integer> attrs = new EnumMap<>(Attr.class);
        attrs.put(Attr.ACCELERATION, pace);
        attrs.put(Attr.SPRINT_SPEED, pace);
        attrs.put(Attr.SHORT_PASSING, passing);
        attrs.put(Attr.BALL_CONTROL, passing);
        attrs.put(Attr.HEADING, air);
        attrs.put(Attr.JUMPING, air);
        attrs.put(Attr.STRENGTH, air);
        attrs.put(Attr.DEF_AWARENESS, awareness);
        return FitPlayer.builder().id(1L).name("P").overall(80).positions(List.of("CB")).attrs(attrs).playstyles(Map.of()).build();
    }

    private IntentProfile slot(String group, String buildUp, Integer depth) {
        TeamSetup setup = buildUp == null && depth == null ? null : TeamSetup.builder().buildUp(buildUp).depth(depth).build();
        return IntentProfile.builder().slotId("S").slotPosition(group).slotGroup(group).tags(List.of()).weaponBoost(Map.of()).setup(setup).build();
    }

    @Test
    void noSetupMeansNoAdjustment() {
        assertThat(modifier.delta(player(50, 50, 50, 50), slot("CB", null, null))).isZero();
    }

    @Test
    void slowCentreBackIsPenalisedOnAHighLineAndFastOneRewarded() {
        double slow = modifier.delta(player(55, 70, 70, 70), slot("CB", "Balanced", 80));
        double fast = modifier.delta(player(80, 70, 70, 70), slot("CB", "Balanced", 80));
        assertThat(slow).isNegative();
        assertThat(fast).isPositive().isLessThanOrEqualTo(2.0);
        assertThat(slow).isGreaterThanOrEqualTo(-6.0);
    }

    @Test
    void lowBlockCaresAboutAirDuelsNotPace() {
        assertThat(modifier.delta(player(40, 70, 80, 70), slot("CB", "Balanced", 20))).isPositive();
        assertThat(modifier.delta(player(90, 70, 50, 70), slot("CB", "Balanced", 20))).isNegative();
    }

    @Test
    void shortBuildUpPenalisesPoorPassersInTheBackLine() {
        assertThat(modifier.delta(player(70, 50, 70, 70), slot("CB", "Short", 50))).isNegative();
        assertThat(modifier.delta(player(70, 80, 70, 70), slot("CDM", "Short", 50))).isPositive();
    }

    @Test
    void counterBuildUpRewardsFastRunnersOnly() {
        assertThat(modifier.delta(player(88, 50, 50, 50), slot("ST", "Counter", 50))).isPositive();
        assertThat(modifier.delta(player(88, 50, 50, 50), slot("CM", "Counter", 50))).isZero();
    }
}
