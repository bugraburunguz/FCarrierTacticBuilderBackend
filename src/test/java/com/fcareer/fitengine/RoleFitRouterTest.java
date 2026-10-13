package com.fcareer.fitengine;

import com.fcareer.fitengine.configuration.FitEngineProperties;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.FitRole;
import com.fcareer.fitengine.model.response.RoleFitResponse;
import com.fcareer.fitengine.service.RoleFitRouter;
import com.fcareer.fitengine.service.RoleFitService;
import com.fcareer.fitengine.service.RoleFitV3Service;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** E3-5b: advisor/mevki önerisi yolları career-engine-version bayrağına bağlı (v3 → V3 servisi, v1 → eski motor; geri alma = bayrak). */
class RoleFitRouterTest {

    private final RoleFitService v1 = mock(RoleFitService.class);
    private final RoleFitV3Service v3 = mock(RoleFitV3Service.class);
    private final FitPlayer player = FitPlayer.builder().id(1L).build();
    private final FitRole role = FitRole.builder().id("poacher_attack").build();

    @Test
    void careerFlagV3RoutesToTheV3Service() {
        FitEngineProperties properties = new FitEngineProperties();
        properties.setCareerEngineVersion("v3");
        RoleFitResponse expected = RoleFitResponse.builder().engineVersion("v3").build();
        when(v3.calculate(player, role, null, "ST")).thenReturn(expected);
        assertThat(new RoleFitRouter(v1, v3, properties).forCareer(player, role, null, "ST")).isSameAs(expected);
        verifyNoInteractions(v1);
    }

    @Test
    void careerFlagV1RollsBackToTheLegacyEngine() {
        FitEngineProperties properties = new FitEngineProperties();
        properties.setCareerEngineVersion("v1");
        RoleFitResponse expected = RoleFitResponse.builder().engineVersion("v1").build();
        when(v1.calculate(player, role, null, "ST")).thenReturn(expected);
        assertThat(new RoleFitRouter(v1, v3, properties).forCareer(player, role, null, "ST")).isSameAs(expected);
        verifyNoInteractions(v3);
    }
}
