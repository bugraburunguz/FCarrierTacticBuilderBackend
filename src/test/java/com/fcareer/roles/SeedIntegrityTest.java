package com.fcareer.roles;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.roles.entity.RoleEntity;
import com.fcareer.roles.seed.ReferenceSeedBundle;
import com.fcareer.roles.seed.RoleLibraryGenerator;
import com.fcareer.roles.seed.SeedReader;
import com.fcareer.roles.seed.SeedValidator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SeedIntegrityTest {

    private final ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final ReferenceSeedBundle bundle = new SeedReader(mapper).read();
    private final RoleLibraryGenerator generator = new RoleLibraryGenerator();
    private final SeedValidator validator = new SeedValidator();

    @Test
    void seedIsInternallyConsistent() {
        List<RoleEntity> roles = generator.generate(bundle.roles(), bundle.weighting());
        Map<String, List<String>> conflicts = validator.symmetricConflicts(bundle.tags());

        assertThat(validator.validate(bundle, roles, conflicts)).isEmpty();
    }

    @Test
    void roleLibraryExpandsFocusVariantsToAtLeast37Roles() {
        List<RoleEntity> roles = generator.generate(bundle.roles(), bundle.weighting());

        assertThat(roles.size()).isGreaterThanOrEqualTo(37);
        assertThat(roles).extracting(RoleEntity::getId).doesNotHaveDuplicates();
        assertThat(roles).extracting(RoleEntity::getId).contains("inside_forward_attack", "box_to_box_ball_winning");
    }

    @Test
    void weaponBendRaisesWeaponWeight() {
        RoleEntity role = generator.generate(bundle.roles(), bundle.weighting()).stream()
                .filter(r -> r.getId().equals("poacher_attack")).findFirst().orElseThrow();
        double finishing = role.getAttrWeights().get("Finishing");

        assertThat(finishing).isGreaterThan(role.getAttrWeights().get("Volleys"));
    }
}
