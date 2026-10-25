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
    void ranksByRoleFitThenStrength_unnormedLastByStrength() {
        // İnceleme: oturma göreli yüzdelik, Güç mutlak — iki ölçek karşılaştırılmaz; normsuz roller sonda, kendi aralarında Güç'le.
        List<RoleAlternative> ranked = RoleSuggestService.rank(List.of(alt("a", 70, 40.0), alt("b", 60, 71.0), alt("c", 80, null), alt("d", 65, 71.0), alt("e", 85, null)));
        assertThat(ranked).extracting(RoleAlternative::getRoleId).containsExactly("d", "b", "a", "e", "c");
    }

    @Test
    void unnormedRoleIsNeverSuggestedNorCountsAsCurrentBasis() {
        List<RoleAlternative> unnormedBest = RoleSuggestService.rank(List.of(alt("cur", 60, 40.0), alt("x", 90, null)));
        assertThat(RoleSuggestService.better("cur", unnormedBest, 10)).isEmpty();
        List<RoleAlternative> unnormedCurrent = RoleSuggestService.rank(List.of(alt("cur", 90, null), alt("y", 60, 70.0)));
        assertThat(RoleSuggestService.better("cur", unnormedCurrent, 10)).as("mevcut rolün oturması bilinmiyor").isEmpty();
    }

    @Test
    void betterRoleOnlyAboveThreshold() {
        List<RoleAlternative> ranked = RoleSuggestService.rank(List.of(alt("cur", 60, 50.0), alt("ifw", 66, 71.0), alt("w", 62, 58.0)));
        assertThat(RoleSuggestService.better("cur", ranked, 10)).map(RoleAlternative::getRoleId).contains("ifw");
        assertThat(RoleSuggestService.better("cur", ranked, 25)).as("fark 21 < 25").isEmpty();
        assertThat(RoleSuggestService.better("ifw", ranked, 10)).as("zaten en iyi rol").isEmpty();
        assertThat(RoleSuggestService.better("yok", ranked, 10)).as("mevcut rol listede yoksa öneri yok").isEmpty();
    }
}
