package com.fcareer.fitengine;

import com.fcareer.fitengine.configuration.FitEngineProperties;
import com.fcareer.fitengine.service.RoleFitV3Service;
import com.fcareer.fitengine.service.RoleFitV3Service.FitLabel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** E3-5b rozet eşikleri (levelFit): ≥65 iyi, 40-65 seviyesinde (renk yok), 20-40 zayıf (sarı), <20 yanlış rol (kırmızı). */
class LevelFitLabelTest {

    private final FitEngineProperties.V3 v3 = new FitEngineProperties().getV3();

    @Test
    void thresholdsFollowTheApprovedBands() {
        assertThat(RoleFitV3Service.label(100, v3)).isEqualTo(FitLabel.GOOD);
        assertThat(RoleFitV3Service.label(65, v3)).isEqualTo(FitLabel.GOOD);
        assertThat(RoleFitV3Service.label(64.9, v3)).isEqualTo(FitLabel.AT_LEVEL);
        assertThat(RoleFitV3Service.label(40, v3)).isEqualTo(FitLabel.AT_LEVEL);
        assertThat(RoleFitV3Service.label(39.9, v3)).isEqualTo(FitLabel.WEAK);
        assertThat(RoleFitV3Service.label(20, v3)).isEqualTo(FitLabel.WEAK);
        assertThat(RoleFitV3Service.label(19.9, v3)).isEqualTo(FitLabel.WRONG_ROLE);
        assertThat(RoleFitV3Service.label(0, v3)).isEqualTo(FitLabel.WRONG_ROLE);
    }

    @Test
    void onlyTheNonNeutralBandsCarryAColor() {
        assertThat(FitLabel.GOOD.badge()).isNotNull();
        assertThat(FitLabel.AT_LEVEL.badge()).isNull();
        assertThat(FitLabel.WEAK.badge()).isNotNull();
        assertThat(FitLabel.WRONG_ROLE.badge()).isNotNull();
    }
}
