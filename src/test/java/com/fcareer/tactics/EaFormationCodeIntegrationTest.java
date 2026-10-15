package com.fcareer.tactics;

import com.fcareer.IntegrationTest;
import com.fcareer.tactics.service.FormationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V3-05d-b: EA formasyon kodu → bizim formasyon kimliği TEK yerde (formations.json `eaFormationId`, FormationRegistry).
 * Buğra'nın yakalamasında görülen beş kod. Dikkat: EA "f451" bizde "4-5-1 (CAM)"; düz "4-5-1" EA'da "f451a".
 */
@IntegrationTest
class EaFormationCodeIntegrationTest {

    @Autowired
    private FormationRegistry registry;

    @Test
    void theFiveObservedEaCodesMapToOurFormations() {
        Map<String, String> byCode = registry.idsByEaCode();
        assertThat(byCode).containsEntry("f41212", "4-1-2-1-2 (geniş)").containsEntry("f442", "4-4-2").containsEntry("f532", "5-3-2")
                .containsEntry("f352", "3-5-2").containsEntry("f451", "4-5-1 (CAM)").containsEntry("f451a", "4-5-1");
        assertThat(byCode).hasSize(registry.findAll().size());
        assertThat(byCode.get("f999")).isNull();
    }
}
