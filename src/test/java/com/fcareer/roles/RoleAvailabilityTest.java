package com.fcareer.roles;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.roles.entity.RoleEntity;
import com.fcareer.roles.seed.ReferenceSeedBundle;
import com.fcareer.roles.seed.RoleLibraryGenerator;
import com.fcareer.roles.seed.SeedReader;
import com.fcareer.tactics.model.FormationDefinition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class RoleAvailabilityTest {

    private static Map<String, RoleEntity> roles;
    private static ReferenceSeedBundle bundle;

    @BeforeAll
    static void load() {
        bundle = new SeedReader(new ObjectMapper()).read();
        roles = new RoleLibraryGenerator().generate(bundle.roles(), bundle.weighting()).stream()
                .collect(Collectors.toMap(RoleEntity::getId, r -> r));
    }

    @Test
    void playmakerAttackExistsOnlyInCentralMidfield() {
        assertThat(roles.get("playmaker_attack").getPositions()).containsExactly("CM");
        assertThat(roles.get("playmaker_balanced").getPositions()).containsExactly("CAM");
        assertThat(roles.get("playmaker_roaming").getPositions()).containsExactlyInAnyOrder("CM", "CAM");
    }

    @Test
    void combinationsMissingFromBothEaTableAndGuideAreNotOffered() {
        assertThat(roles.get("deep_lying_playmaker_roaming").getPositions()).doesNotContain("CM");
        assertThat(roles.get("half_winger_roaming").getPositions()).containsExactly("CAM");
        assertThat(roles.get("half_winger_support").getPositions()).containsExactly("CM");
        assertThat(roles.get("half_winger_balanced").getPositions()).doesNotContain("LM", "RM");
        assertThat(roles.get("winger_versatile").getPositions()).containsExactlyInAnyOrder("LW", "RW");
        assertThat(roles.get("inside_forward_roaming").getPositions()).containsExactlyInAnyOrder("LW", "RW");
    }

    @Test
    void everyRoleStillHasAtLeastOnePosition() {
        roles.values().forEach(role -> assertThat(role.getPositions()).as(role.getId()).isNotEmpty());
    }

    @Test
    void everyFormationSlotDefaultRoleIsValidAtItsPosition() {
        List<FormationDefinition> formations = new SeedReader(new ObjectMapper()).readFormations();
        formations.forEach(formation -> formation.slots().forEach(slot -> {
            RoleEntity role = roles.get(slot.defaultRole());
            assertThat(role).as(formation.id() + "/" + slot.slotId()).isNotNull();
            assertThat(role.getPositions()).as(formation.id() + "/" + slot.slotId() + " " + slot.defaultRole()).contains(slot.position());
        }));
    }

    @Test
    void everyBehaviorTagVoteIsAvailableAtItsPositionGroup() {
        bundle.tags().forEach(tag -> {
            if (tag.roleVote() == null) {
                return;
            }
            RoleEntity role = roles.get(tag.roleVote());
            assertThat(role).as(tag.id()).isNotNull();
            assertThat(role.getPositions()).as(tag.id() + " -> " + tag.roleVote()).isNotEmpty();
        });
    }
}
