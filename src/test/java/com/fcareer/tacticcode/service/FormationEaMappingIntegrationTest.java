package com.fcareer.tacticcode.service;

import com.fcareer.IntegrationTest;
import com.fcareer.tacticcode.model.EaCodecData;
import com.fcareer.tactics.model.FormationDefinition;
import com.fcareer.tactics.model.FormationSlot;
import com.fcareer.tactics.model.request.TacticRequest;
import com.fcareer.tactics.service.FormationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class FormationEaMappingIntegrationTest {

    @Autowired
    private EaTacticCodec codec;
    @Autowired
    private FormationRegistry formationRegistry;
    @Autowired
    private TacticCodeService tacticCodeService;

    @Test
    void everyFormationNamesItsEaFormationAndTheShapeMatches() {
        Set<String> used = new HashSet<>();
        for (FormationDefinition ours : formationRegistry.findAll()) {
            assertThat(ours.eaFormationId()).as(ours.id()).isNotBlank();
            EaCodecData.Formation ea = codec.formation(ours.eaFormationId()).orElseThrow(() -> new AssertionError(ours.id() + " -> " + ours.eaFormationId()));
            assertThat(used.add(ours.eaFormationId())).as("duplicate " + ours.eaFormationId()).isTrue();
            List<String> ourPositions = ours.slots().stream().map(FormationSlot::position).sorted().toList();
            assertThat(ea.positions().stream().sorted().toList()).as(ours.id() + " shape vs " + ea.label()).isEqualTo(ourPositions);
        }
        assertThat(used).hasSize(codec.formations().size());
    }

    @Test
    void fiveThreeTwoExportsAsTheEaFiveThreeTwo() {
        String code = tacticCodeService.export(TacticRequest.builder().formation("5-3-2").build()).getCode();

        assertThat(codec.decode(code).orElseThrow().formationId()).isEqualTo("f532");
    }
}
