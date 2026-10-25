package com.fcareer.advisor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.advisor.configuration.PositionProperties;
import com.fcareer.advisor.model.response.AdvisorResponses.PlayerPositionAdvice;
import com.fcareer.advisor.service.PositionAdviceService;
import com.fcareer.advisor.service.SideScorer;
import com.fcareer.career.desk.SpoilerPolicy;
import com.fcareer.career.service.CareerService;
import com.fcareer.catalog.service.PlayerService;
import com.fcareer.catalog.service.PositionOverallService;
import com.fcareer.development.plan.DevelopmentPlans;
import com.fcareer.development.plan.PlanProjector;
import com.fcareer.development.recommend.DevelopmentRecommender;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.FitRole;
import com.fcareer.fitengine.model.WeaponState;
import com.fcareer.fitengine.model.response.RoleFitResponse;
import com.fcareer.fitengine.service.RoleFitRouter;
import com.fcareer.roles.service.RoleService;
import com.fcareer.usertactic.UserTacticService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * FIT-3 kararı (2): dönüşüm için sabit alt sınır yok (alt ligde herkes düşük). Önerilen mevkideki Güç, takımın ilk 11'inin Güç alt üçte birinin üst
 * sınırını geçmiyorsa öneri çıkmaz. Gençlerde (≤ 23, POT ≥ OVR+5) 2 sezon sonraki tahmini Güç kullanılır.
 */
class ConversionGateTest {

    private static final Map<String, Integer> OVERALLS = Map.of("LM", 68, "RM", 68, "CDM", 68, "CM", 60);

    private PositionAdviceService service() {
        RoleService roles = mock(RoleService.class);
        Map<String, FitRole> byId = new LinkedHashMap<>();
        byId.put("winger_balanced", FitRole.builder().id("winger_balanced").baseRole("winger").name("Winger (Balanced)").positions(List.of("LM", "RM")).build());
        byId.put("holding_defend", FitRole.builder().id("holding_defend").baseRole("holding").name("Holding (Defend)").positions(List.of("CDM")).build());
        when(roles.fitRolesById()).thenReturn(byId);
        RoleFitRouter router = mock(RoleFitRouter.class);
        // LM/RM 64; CDM 72 + (OVR − 68): projeksiyonda OVR artınca CDM Güç'ü de artar.
        when(router.forCareer(any(), any(), any(), anyString())).thenAnswer(inv -> {
            FitPlayer p = inv.getArgument(0);
            String position = inv.getArgument(3);
            double score = "CDM".equals(position) ? 72 + (p.getOverall() - 68) : 64;
            return RoleFitResponse.builder().score(score).badge(WeaponState.GREEN).reasons(List.of()).build();
        });
        PositionOverallService overall = mock(PositionOverallService.class);
        when(overall.byPosition(any(), anyBoolean(), anyString(), anyInt())).thenReturn(OVERALLS);
        SideScorer scorer = mock(SideScorer.class);
        when(scorer.sideBests(any(), anyString(), anyString(), any())).thenReturn(Map.of());
        PlanProjector projector = mock(PlanProjector.class);
        when(projector.project(any(), any(), eq(2))).thenAnswer(inv -> {
            FitPlayer p = inv.getArgument(0);
            return new PlanProjector.PlanProjection(p.toBuilder().overall(p.getOverall() + 10).build(), p.getOverall() + 10);
        });
        return new PositionAdviceService(roles, router, mock(CareerService.class), mock(PlayerService.class), overall, new PositionProperties(), scorer,
                new DevelopmentPlans(new ObjectMapper()), projector, new DevelopmentRecommender.Config(), mock(UserTacticService.class), mock(SpoilerPolicy.class));
    }

    private static FitPlayer lm(int age, Integer potential) {
        return FitPlayer.builder().id(1L).name("Kanat").age(age).overall(68).potential(potential).positions(List.of("LM")).attrs(Map.of()).preferredFoot("Right").build();
    }

    private PlayerPositionAdvice advise(FitPlayer p, Double cutoff) {
        return service().advise(List.of(p), cutoff).get(0);
    }

    @Test
    void lowerThirdCutoffIsTheTopOfTheStartingElevensBottomThird() {
        assertThat(PositionAdviceService.lowerThirdCutoff(List.of(50.0, 52.0, 55.0, 60.0, 61.0, 62.0, 63.0, 64.0, 65.0, 70.0, 72.0))).isEqualTo(55.0);
        assertThat(PositionAdviceService.lowerThirdCutoff(List.of(60.0, 61.0))).as("üçte bir boş → eşik yok").isNull();
    }

    @Test
    void conversionNeedsToBeatTheBottomThirdOfTheFirstEleven() {
        assertThat(advise(lm(28, 70), 70.0).getConversion()).as("CDM 72 > 70").isNotNull();
        assertThat(advise(lm(28, 70), 72.0).getConversion()).as("CDM 72, alt üçte bir 72 → ilk 11'e girmez").isNull();
        assertThat(advise(lm(28, 70), null).getConversion()).as("eşik bilinmiyorsa eski kural").isNotNull();
    }

    @Test
    void youngstersAreJudgedOnTheirTwoSeasonProjection() {
        // Bugün CDM 72 eşiğin (75) altında; 2 sezon sonra (OVR +10 → CDM 82) geçiyor.
        assertThat(advise(lm(19, 80), 75.0).getConversion()).isNotNull();
        assertThat(advise(lm(28, 80), 75.0).getConversion()).as("genç değil: bugünkü Güç").isNull();
        assertThat(advise(lm(19, 70), 75.0).getConversion()).as("baş boşluğu 2 < 5: genç sayılmaz").isNull();
    }
}
