package com.fcareer.fitengine;

import com.fcareer.fitengine.model.response.RoleAlternative;
import com.fcareer.fitengine.service.RoleSuggestService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FIT-2 madde 6: rol önerici. Sıra Rol oturması (levelFit; norm yoksa Güç) azalan, eşitlikte Güç. "Daha uygun rol" yalnız en iyi rol mevcut rolden
 * en az `minGain` puan iyiyse ve farklı bir rolse.
 */
class RoleSuggestRulesTest {

    private static RoleAlternative alt(String roleId, double currentFit, Double levelFit) {
        return RoleAlternative.builder().roleId(roleId).roleName(roleId).currentFit(currentFit).levelFit(levelFit).build();
    }

    @Test
    void ranksByStrengthThenRoleFit() {
        List<RoleAlternative> ranked = RoleSuggestService.rank(List.of(alt("a", 70, 40.0), alt("b", 60, 71.0), alt("c", 80, null), alt("d", 70, 71.0), alt("e", 85, null)));
        assertThat(ranked).extracting(RoleAlternative::getRoleId).containsExactly("e", "c", "d", "a", "b");
    }

    @Test
    void ranksEspartsLeftBackRolesByStrengthNotByLevel() {
        List<RoleAlternative> ranked = RoleSuggestService.rank(List.of(alt("fullback_versatile", 77.5, 91.9), alt("fullback_defend", 80.6, 83.8), alt("wingback_balanced", 66.0, 48.7)));
        assertThat(ranked).extracting(RoleAlternative::getRoleId).containsExactly("fullback_defend", "fullback_versatile", "wingback_balanced");
        assertThat(RoleSuggestService.better("wingback_balanced", ranked, 10)).map(RoleAlternative::getRoleId).contains("fullback_defend");
    }

    @Test
    void unnormedRolesAreComparedByStrength() {
        List<RoleAlternative> unnormedBest = RoleSuggestService.rank(List.of(alt("cur", 60, 40.0), alt("x", 90, null)));
        assertThat(RoleSuggestService.better("cur", unnormedBest, 10)).map(RoleAlternative::getRoleId).contains("x");
        List<RoleAlternative> unnormedCurrent = RoleSuggestService.rank(List.of(alt("cur", 50, null), alt("y", 65, 70.0)));
        assertThat(RoleSuggestService.better("cur", unnormedCurrent, 10)).map(RoleAlternative::getRoleId).contains("y");
    }

    @Test
    void betterRoleOnlyAboveStrengthThreshold() {
        List<RoleAlternative> ranked = RoleSuggestService.rank(List.of(alt("cur", 60, 90.0), alt("ifw", 72, 50.0), alt("w", 62, 58.0)));
        assertThat(RoleSuggestService.better("cur", ranked, 10)).map(RoleAlternative::getRoleId).contains("ifw");
        assertThat(RoleSuggestService.better("cur", ranked, 15)).as("Güç farkı 12 < 15").isEmpty();
        assertThat(RoleSuggestService.better("ifw", ranked, 10)).as("zaten en güçlü rol").isEmpty();
        assertThat(RoleSuggestService.better("yok", ranked, 10)).as("mevcut rol listede yoksa öneri yok").isEmpty();
    }
}
