package com.fcareer.advisor;

import com.fcareer.advisor.model.response.AdvisorResponses.Conversion;
import com.fcareer.advisor.model.response.AdvisorResponses.PositionOption;
import com.fcareer.advisor.service.ConversionRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FIT-3 madde 12: en iyi mevki v2 — dönüşüm önerisi. Listede olmayan bir mevkideki en iyi Güç, listedekinin en iyisini eşik (5) kadar geçiyor ve EA OVR'si
 * o mevkide oyuncunun OVR'sinden en çok N (3) düşükse öneri çıkar. Ayna mevki (LM listeli → RM) dönüşüm sayılmaz. "En iyi mevki" (EA OVR) değişmez.
 */
class ConversionRuleTest {

    private static PositionOption option(String position, String role, double score, boolean natural) {
        return PositionOption.builder().position(position).roleName(role).score(score).natural(natural).reasons(List.of()).build();
    }

    /** LM listeli, CDM profilli sentetik oyuncu. */
    private static final List<PositionOption> LM_WITH_CDM_PROFILE = List.of(
            option("CDM", "Holding (Defend)", 72, false), option("CM", "Box to Box (Balanced)", 68, false), option("LM", "Winger (Balanced)", 64, true),
            option("RM", "Winger (Balanced)", 63, false));

    @Test
    void lmListedPlayerWithACdmProfileGetsAConversion() {
        Optional<Conversion> c = ConversionRule.find(LM_WITH_CDM_PROFILE, List.of("LM"), Map.of("CDM", 68, "LM", 70, "RM", 70, "CM", 69), 70, 5, 3,
                (pos) -> Optional.of("CDM mevki planı"));
        assertThat(c).isPresent();
        assertThat(c.get().getPosition()).isEqualTo("CDM");
        assertThat(c.get().getFromPosition()).as("kıyas listelenen bölgenin (ayna dahil) en iyisi").isEqualTo("LM");
        assertThat(c.get().getText()).isEqualTo("LM listeli ama CDM'de daha etkili: CDM · Holding gücü 72, LM · Winger 64. CDM mevki planı önerilir.");
        assertThat(c.get().getPlanName()).isEqualTo("CDM mevki planı");
    }

    @Test
    void belowTheGainThresholdNoConversion() {
        List<PositionOption> close = List.of(option("CDM", "Holding (Defend)", 68, false), option("LM", "Winger (Balanced)", 64, true));
        assertThat(ConversionRule.find(close, List.of("LM"), Map.of("CDM", 70, "LM", 70), 70, 5, 3, (pos) -> Optional.empty())).isEmpty();
    }

    @Test
    void tooLowEaOverallAtTheNewPositionNoConversion() {
        List<PositionOption> far = List.of(option("CDM", "Holding (Defend)", 80, false), option("LM", "Winger (Balanced)", 64, true));
        assertThat(ConversionRule.find(far, List.of("LM"), Map.of("CDM", 66, "LM", 70), 70, 5, 3, (pos) -> Optional.empty())).as("OVR 4 düşük").isEmpty();
    }

    @Test
    void mirrorSideIsNotAConversion() {
        List<PositionOption> mirror = List.of(option("RM", "Winger (Balanced)", 75, false), option("LM", "Winger (Balanced)", 64, true));
        assertThat(ConversionRule.find(mirror, List.of("LM"), Map.of("RM", 70, "LM", 70), 70, 5, 3, (pos) -> Optional.empty())).isEmpty();
    }
}
