package com.fcareer.advisor;

import com.fcareer.advisor.configuration.PositionProperties;
import com.fcareer.advisor.model.response.AdvisorResponses.PlayerPositionAdvice;
import com.fcareer.advisor.model.response.AdvisorResponses.PositionOption;
import com.fcareer.advisor.service.PositionAdviceService;
import com.fcareer.advisor.service.SideChoice;
import com.fcareer.advisor.service.SideScorer;
import com.fcareer.development.plan.DevelopmentPlans;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.career.service.CareerService;
import com.fcareer.catalog.service.PlayerService;
import com.fcareer.catalog.service.PositionOverallService;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.FitRole;
import com.fcareer.fitengine.model.WeaponState;
import com.fcareer.fitengine.model.response.RoleFitResponse;
import com.fcareer.fitengine.service.RoleFitRouter;
import com.fcareer.roles.service.RoleService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * KARIYER-UX FAZ 3.1: PositionAdviceService "en iyi mevki" (bestOverallPosition) ve öneri listesi (best, ilk 3) simetrik eşitlikte sola kaymaz.
 * Mevki overall'ları ve rol puanları simetrik mevkilerde eşit verilir (gerçek hesapta da eşit).
 */
class PositionAdviceSideTest {

    private PositionAdviceService service(Map<String, Integer> overalls, int listedPreference) {
        return service(overalls, listedPreference, Map.of());
    }

    /** sides: SideScorer'ın iki kanat için döndüreceği en iyi rol Güç'leri (boş → tarz kararı yok, eski kural). */
    private PositionAdviceService service(Map<String, Integer> overalls, int listedPreference, Map<String, SideChoice.Best> sides) {
        RoleService roles = mock(RoleService.class);
        FitRole fullback = FitRole.builder().id("fullback_balanced").name("Fullback (Balanced)").positions(List.of("LB", "RB")).build();
        FitRole defender = FitRole.builder().id("defender_defend").name("Defender (Defend)").positions(List.of("CB")).build();
        Map<String, FitRole> byId = new LinkedHashMap<>();
        byId.put(fullback.getId(), fullback);
        byId.put(defender.getId(), defender);
        when(roles.fitRolesById()).thenReturn(byId);
        RoleFitRouter router = mock(RoleFitRouter.class);
        when(router.forCareer(any(), any(), any(), anyString())).thenAnswer(inv -> {
            String position = inv.getArgument(3);
            double score = "CB".equals(position) ? 70 : 72;
            return RoleFitResponse.builder().score(score).badge(WeaponState.GREEN).reasons(List.of()).build();
        });
        PositionOverallService overallService = mock(PositionOverallService.class);
        when(overallService.byPosition(any(), anyBoolean(), anyString(), anyInt())).thenReturn(overalls);
        PositionProperties properties = new PositionProperties();
        properties.setListedPreference(listedPreference);
        SideScorer scorer = mock(SideScorer.class);
        when(scorer.sideBests(any(), anyString(), anyString(), any())).thenReturn(sides);
        return new PositionAdviceService(roles, router, mock(CareerService.class), mock(PlayerService.class), overallService, properties, scorer,
                new DevelopmentPlans(new ObjectMapper()), mock(com.fcareer.development.plan.PlanProjector.class), new com.fcareer.development.recommend.DevelopmentRecommender.Config(),
                mock(com.fcareer.usertactic.UserTacticService.class), mock(com.fcareer.career.desk.SpoilerPolicy.class), mock(org.springframework.beans.factory.ObjectProvider.class));
    }

    private static FitPlayer player(List<String> positions, String foot) {
        return FitPlayer.builder().id(1L).name("McGowan").overall(68).positions(positions).attrs(Map.of()).preferredFoot(foot).build();
    }

    @Test
    void bestOverallAndAdviceListPreferTheListedSide() {
        Map<String, Integer> overalls = new LinkedHashMap<>();
        overalls.put("LB", 68);
        overalls.put("RB", 68);
        overalls.put("CB", 67);
        PlayerPositionAdvice advice = service(overalls, 1).advise(List.of(player(List.of("CB", "RB"), "Right"))).get(0);

        assertThat(advice.getBestOverallPosition()).isEqualTo("RB");
        assertThat(advice.getBest().stream().map(PositionOption::getPosition).toList()).as("eşit puanlı LB/RB'de RB önce").startsWith("RB", "LB");
    }

    @Test
    void listedPreferenceThresholdComesFromConfig() {
        Map<String, Integer> overalls = new LinkedHashMap<>();
        overalls.put("LB", 69);
        overalls.put("RB", 69);
        overalls.put("CB", 68);
        assertThat(service(overalls, 1).advise(List.of(player(List.of("CB"), "Left"))).get(0).getBestOverallPosition()).isEqualTo("CB");
        assertThat(service(overalls, 0).advise(List.of(player(List.of("CB"), "Left"))).get(0).getBestOverallPosition()).isEqualTo("LB");
    }

    /** FIT-3 madde 13: iki kanadın en iyi rol Güç farkı eşiği geçerse taraf tarzla seçilir ve gerekçe yazılır; EA OVR aynı kalır. */
    @Test
    void wideSideFollowsStyleWhenTheGapPassesTheThreshold() {
        Map<String, Integer> overalls = new LinkedHashMap<>();
        overalls.put("LB", 68);
        overalls.put("RB", 68);
        overalls.put("CB", 60);
        FitPlayer p = FitPlayer.builder().id(1L).name("Bek").overall(68).positions(List.of("LB")).attrs(Map.of(com.fcareer.fitengine.model.Attr.CROSSING, 78))
                .preferredFoot("Right").build();
        PlayerPositionAdvice styled = service(overalls, 1, Map.of("LB", new SideChoice.Best("Fullback (Balanced)", 70), "RB", new SideChoice.Best("Wingback (Support)", 72)))
                .advise(List.of(p)).get(0);
        assertThat(styled.getBestOverallPosition()).isEqualTo("RB");
        assertThat(styled.getBestOverall()).isEqualTo(68);
        assertThat(styled.getSideReason()).endsWith("+ sağ ayak → RB · Wingback");
        PlayerPositionAdvice tie = service(overalls, 1, Map.of("LB", new SideChoice.Best("Fullback (Balanced)", 70), "RB", new SideChoice.Best("Fullback (Balanced)", 70.3)))
                .advise(List.of(p)).get(0);
        assertThat(tie.getBestOverallPosition()).as("eşik altı: listedeki taraf").isEqualTo("LB");
        assertThat(tie.getSideReason()).isNull();
        assertThat(tie.getConversion()).isNull();
    }
}
