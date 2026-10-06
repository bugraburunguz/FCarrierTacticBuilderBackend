package com.fcareer.fitengine;

import com.fcareer.fitengine.model.FitRole;
import com.fcareer.fitengine.model.IntentProfile;
import com.fcareer.fitengine.service.TacticMirror;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TacticMirrorTest {

    private IntentProfile slot(String slotId, String position, String roleId) {
        return IntentProfile.builder().slotId(slotId).slotPosition(position).slotGroup("W").tags(List.of()).weaponBoost(Map.of())
                .resolvedRole(FitRole.builder().id(roleId).build()).build();
    }

    @Test
    void swapsRolesBetweenLeftAndRightSlotsAndLeavesCentralSlotsAlone() {
        List<IntentProfile> slots = List.of(slot("GK", "GK", "keeper"), slot("LW", "LW", "inside_forward"),
                slot("ST", "ST", "poacher"), slot("RW", "RW", "winger_attack"));

        List<IntentProfile> mirrored = TacticMirror.mirror(slots);

        assertThat(mirrored).extracting(IntentProfile::getSlotId).containsExactly("GK", "LW", "ST", "RW");
        assertThat(mirrored.get(1).getResolvedRole().getId()).isEqualTo("winger_attack");
        assertThat(mirrored.get(3).getResolvedRole().getId()).isEqualTo("inside_forward");
        assertThat(mirrored.get(0).getResolvedRole().getId()).isEqualTo("keeper");
        assertThat(mirrored.get(2).getResolvedRole().getId()).isEqualTo("poacher");
        assertThat(mirrored.get(1).getSlotPosition()).isEqualTo("LW");
    }

    @Test
    void symmetricTacticsAreNotMirrorable() {
        List<IntentProfile> slots = List.of(slot("LW", "LW", "winger_attack"), slot("RW", "RW", "winger_attack"), slot("ST", "ST", "poacher"));

        assertThat(TacticMirror.isMirrorable(slots)).isFalse();
        assertThat(TacticMirror.isMirrorable(List.of(slot("LW", "LW", "a"), slot("RW", "RW", "b")))).isTrue();
        assertThat(TacticMirror.isMirrorable(List.of(slot("LW", "LW", "a"), slot("ST", "ST", "b")))).isFalse();
    }
}
