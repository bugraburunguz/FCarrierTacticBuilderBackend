package com.fcareer.tacticcode.service;

import com.fcareer.IntegrationTest;
import com.fcareer.common.exception.InvalidTacticException;
import com.fcareer.fitengine.model.FitRole;
import com.fcareer.fitengine.model.IntentProfile;
import com.fcareer.roles.service.RoleService;
import com.fcareer.tactics.model.FormationDefinition;
import com.fcareer.tactics.model.request.TacticRequest;
import com.fcareer.tactics.service.FormationRegistry;
import com.fcareer.tactics.service.TacticResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@IntegrationTest
class TacticCodeExportIntegrationTest {

    @Autowired
    private EaTacticCodec codec;
    @Autowired
    private FormationRegistry formationRegistry;
    @Autowired
    private RoleService roleService;

    private TacticCodeService serviceWithRoleFor(String position, String baseRole, String focus) {
        FormationDefinition formation = formationRegistry.get("4-4-2");
        List<IntentProfile> profiles = formation.slots().stream().map(slot -> IntentProfile.builder().slotId(slot.slotId()).slotPosition(slot.position())
                .resolvedRole(slot.position().equals(position)
                        ? FitRole.builder().id(baseRole + "_" + focus).baseRole(baseRole).name(baseRole).focus(focus).positions(List.of(position)).build()
                        : roleService.fitRolesById().get(slot.defaultRole()))
                .build()).toList();
        TacticResolver resolver = mock(TacticResolver.class);
        when(resolver.resolve(any())).thenReturn(new TacticResolver.ResolvedTactic("4-4-2", null, profiles));
        return new TacticCodeService(codec, resolver, formationRegistry, roleService);
    }

    @Test
    void roleMissingInTheGameForThatPositionIsRejectedInsteadOfSilentlyReplaced() {
        TacticCodeService service = serviceWithRoleFor("ST", "Inside Forward", "Balanced");

        assertThatThrownBy(() -> service.export(TacticRequest.builder().formation("4-4-2").build()))
                .isInstanceOfSatisfying(InvalidTacticException.class, e -> assertThat(e.httpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void focusMissingInTheGameForThatRoleIsRejectedInsteadOfSilentlyReplaced() {
        TacticCodeService service = serviceWithRoleFor("ST", "Poacher", "Wide");

        assertThatThrownBy(() -> service.export(TacticRequest.builder().formation("4-4-2").build()))
                .isInstanceOfSatisfying(InvalidTacticException.class, e -> assertThat(e.httpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }
}
