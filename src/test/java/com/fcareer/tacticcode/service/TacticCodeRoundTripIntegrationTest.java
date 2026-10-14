package com.fcareer.tacticcode.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.IntegrationTest;
import com.fcareer.fitengine.model.TeamSetup;
import com.fcareer.tacticcode.model.TacticCodeModels.ExportResponse;
import com.fcareer.tacticcode.model.TacticCodeModels.ImportResponse;
import com.fcareer.tactics.model.request.SlotSelection;
import com.fcareer.tactics.model.request.TacticRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Kod oyunda sadece formasyonu getiriyor" şikâyetinin regresyon kalkanı: oyundan gelen kodu BİZİM modelimize
 * (formasyon id + slotId -> roleId + setup) import eder, sonra aynı modelden export eder; kod birebir aynı olmalı.
 * Codec doğruysa (EaTacticCodecEasySbcVectorsTest) ve bu test kırılıyorsa hata eşleme katmanındadır
 * (slot sırası, rol adı, focus, TacticResolver'ın roleId'yi ezmesi vb.).
 */
@IntegrationTest
class TacticCodeRoundTripIntegrationTest {

    @Autowired
    private TacticCodeService tacticCodeService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void importThenExportReturnsTheSameCodeForEveryPublishedTactic() throws Exception {
        JsonNode vectors = objectMapper.createArrayNode();
        for (String file : List.of("tactic-code/easysbc-vectors.json", "tactic-code/in-game-vectors.json")) {
            try (InputStream in = new ClassPathResource(file).getInputStream()) {
                ((com.fasterxml.jackson.databind.node.ArrayNode) vectors).addAll((com.fasterxml.jackson.databind.node.ArrayNode) objectMapper.readTree(in).path("vectors"));
            }
        }
        assertThat(vectors).as("64 EasySBC + 10 oyun içi T kodu").hasSize(74);
        List<String> failures = new ArrayList<>();
        for (JsonNode v : vectors) {
            String code = v.path("code").asText();
            ImportResponse imported = tacticCodeService.importCode(code);
            if (!imported.isExact()) {
                failures.add(code + " import not exact: " + imported.getWarnings());
                continue;
            }
            List<SlotSelection> slots = imported.getSlots().stream()
                    .map(s -> SlotSelection.builder().slotId(s.getSlotId()).roleId(s.getRoleId()).build())
                    .toList();
            TacticRequest request = TacticRequest.builder()
                    .formation(imported.getFormation())
                    .setup(TeamSetup.builder().buildUp(imported.getBuildUp()).depth(imported.getDepth()).build())
                    .slots(slots)
                    .build();
            ExportResponse exported = tacticCodeService.export(request);
            if (!code.equals(exported.getCode()) || !exported.isExact()) {
                failures.add(code + " -> " + exported.getCode() + " warnings " + exported.getWarnings());
            }
        }
        assertThat(failures).isEmpty();
    }

    /** 14 §2 / §6: `SYq8#eNaswmP` import edilince 11 rol sahada doğru slotlarda. */
    @Test
    void theSampleCodeImportsTheElevenRolesIntoTheRightSlots() {
        ImportResponse imported = tacticCodeService.importCode("SYq8#eNaswmP");
        assertThat(imported.isExact()).isTrue();
        assertThat(imported.getFormation()).isEqualTo("4-2-3-1 (2)");
        assertThat(imported.getBuildUp()).isEqualTo("Balanced");
        assertThat(imported.getDepth()).isEqualTo(25);
        java.util.Map<String, String> roles = new java.util.HashMap<>();
        imported.getSlots().forEach((s) -> roles.put(s.getSlotId(), s.getRoleId()));
        assertThat(roles).containsEntry("GK", "goalkeeper_balanced").containsEntry("RB", "fullback_balanced").containsEntry("RCB", "stopper_balanced")
                .containsEntry("LCB", "defender_defend").containsEntry("LB", "fullback_balanced").containsEntry("RDM", "deep_lying_playmaker_defend")
                .containsEntry("LDM", "deep_lying_playmaker_roaming").containsEntry("RM", "wide_midfielder_support").containsEntry("LM", "wide_midfielder_support")
                .containsEntry("CAM", "playmaker_balanced").containsEntry("ST", "advanced_forward_attack");
    }
}
