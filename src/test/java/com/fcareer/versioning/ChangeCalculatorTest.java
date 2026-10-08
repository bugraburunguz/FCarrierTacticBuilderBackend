package com.fcareer.versioning;

import com.fcareer.versioning.model.ChangeType;
import com.fcareer.versioning.model.PlayerChange;
import com.fcareer.versioning.model.VersionSnapshot;
import com.fcareer.versioning.service.ChangeCalculator;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeCalculatorTest {

    private static VersionSnapshot snap(int overall, String positions, String accel, Integer club, Map<String, Integer> attrs, Map<String, Integer> styles) {
        return new VersionSnapshot(overall, null, accel, positions, club, attrs, styles);
    }

    private static final Map<String, Integer> ATTRS = Map.of("Finishing", 93, "Pace", 90);

    @Test
    void noChangeReturnsNull() {
        VersionSnapshot s = snap(91, "ST,LW", "EXPLOSIVE", 1, ATTRS, Map.of("Rapid", 1));
        assertThat(ChangeCalculator.diff(1L, s, s)).isNull();
    }

    @Test
    void overallDecidesDirection() {
        PlayerChange up = ChangeCalculator.diff(1L, snap(90, "ST", "LENGTHY", 1, ATTRS, Map.of()), snap(91, "ST", "LENGTHY", 1, ATTRS, Map.of()));
        PlayerChange down = ChangeCalculator.diff(1L, snap(91, "ST", "LENGTHY", 1, ATTRS, Map.of()), snap(90, "ST", "LENGTHY", 1, ATTRS, Map.of()));
        assertThat(up.type()).isEqualTo(ChangeType.UPGRADE);
        assertThat(down.type()).isEqualTo(ChangeType.DOWNGRADE);
    }

    @Test
    void whenOverallEqualsNetAttributeSumDecides() {
        PlayerChange up = ChangeCalculator.diff(1L, snap(90, "ST", "LENGTHY", 1, Map.of("Finishing", 93, "Pace", 90), Map.of()),
                snap(90, "ST", "LENGTHY", 1, Map.of("Finishing", 95, "Pace", 90), Map.of()));
        PlayerChange down = ChangeCalculator.diff(1L, snap(90, "ST", "LENGTHY", 1, Map.of("Finishing", 93, "Pace", 90), Map.of()),
                snap(90, "ST", "LENGTHY", 1, Map.of("Finishing", 93, "Pace", 87), Map.of()));
        assertThat(up.type()).isEqualTo(ChangeType.UPGRADE);
        assertThat(up.attributes()).singleElement().satisfies(a -> assertThat(a.delta()).isEqualTo(2));
        assertThat(down.type()).isEqualTo(ChangeType.DOWNGRADE);
    }

    @Test
    void balancedAttributeChangesOrPositionOrPlaystyleOnlyAreMixed() {
        PlayerChange balanced = ChangeCalculator.diff(1L, snap(90, "ST", "LENGTHY", 1, Map.of("Finishing", 93, "Pace", 90), Map.of()),
                snap(90, "ST", "LENGTHY", 1, Map.of("Finishing", 95, "Pace", 88), Map.of()));
        PlayerChange positionOnly = ChangeCalculator.diff(1L, snap(90, "ST", "LENGTHY", 1, ATTRS, Map.of()), snap(90, "ST,LW", "LENGTHY", 1, ATTRS, Map.of()));
        PlayerChange styleOnly = ChangeCalculator.diff(1L, snap(90, "ST", "LENGTHY", 1, ATTRS, Map.of("Rapid", 1)), snap(90, "ST", "LENGTHY", 1, ATTRS, Map.of("Rapid", 2)));
        assertThat(balanced.type()).isEqualTo(ChangeType.MIXED);
        assertThat(positionOnly.type()).isEqualTo(ChangeType.MIXED);
        assertThat(styleOnly.type()).isEqualTo(ChangeType.MIXED);
        assertThat(styleOnly.playstyles().upgraded()).hasSize(1);
    }

    @Test
    void clubChangeAloneIsTransferButWithOtherChangesTypeFollowsTheOtherRules() {
        PlayerChange transfer = ChangeCalculator.diff(1L, snap(90, "ST", "LENGTHY", 1, ATTRS, Map.of()), snap(90, "ST", "LENGTHY", 2, ATTRS, Map.of()));
        PlayerChange transferAndUp = ChangeCalculator.diff(1L, snap(90, "ST", "LENGTHY", 1, ATTRS, Map.of()), snap(91, "ST", "LENGTHY", 2, ATTRS, Map.of()));
        assertThat(transfer.type()).isEqualTo(ChangeType.TRANSFER);
        assertThat(transferAndUp.type()).isEqualTo(ChangeType.UPGRADE);
        assertThat(transferAndUp.after().clubId()).isEqualTo(2);
    }

    @Test
    void unknownIncomingFieldsDoNotCreateFakeChanges() {
        VersionSnapshot before = snap(90, "ST", "EXPLOSIVE", 1, ATTRS, Map.of());
        VersionSnapshot incoming = new VersionSnapshot(91, null, null, "ST", null, ATTRS, Map.of());
        PlayerChange change = ChangeCalculator.diff(1L, before, incoming);
        assertThat(change.type()).isEqualTo(ChangeType.UPGRADE);
        assertThat(change.after().accelerate()).isEqualTo("EXPLOSIVE");
        assertThat(change.after().clubId()).isEqualTo(1);
    }

    @Test
    void playstyleAddedRemovedAndLabels() {
        PlayerChange change = ChangeCalculator.diff(1L, snap(90, "ST", "LENGTHY", 1, ATTRS, Map.of("PowerShot", 1)),
                snap(90, "ST", "LENGTHY", 1, ATTRS, Map.of("FinesseShot", 2)));
        assertThat(change.playstyles().added()).containsExactly("FinesseShot+");
        assertThat(change.playstyles().removed()).containsExactly("PowerShot");
    }
}
