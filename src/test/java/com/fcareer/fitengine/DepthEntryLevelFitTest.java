package com.fcareer.fitengine;

import com.fcareer.fitengine.model.AcceleRate;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.FitRole;
import com.fcareer.fitengine.model.IntentProfile;
import com.fcareer.fitengine.model.response.DepthEntry;
import com.fcareer.fitengine.model.response.RoleFitResponse;
import com.fcareer.fitengine.model.response.SquadFitResponse;
import com.fcareer.fitengine.service.FitEngineV3;
import com.fcareer.fitengine.service.RoleFitV3Service;
import com.fcareer.fitengine.service.SquadFitService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.fcareer.fitengine.FitFixtures.HUNGARIAN;
import static com.fcareer.fitengine.FitFixtures.INTENT_FIT;
import static com.fcareer.fitengine.FitFixtures.NLG;
import static com.fcareer.fitengine.FitFixtures.PROPERTIES;
import static com.fcareer.fitengine.FitFixtures.ROLE_FIT;
import static com.fcareer.fitengine.FitFixtures.player;
import static com.fcareer.fitengine.FitFixtures.slot;
import static com.fcareer.fitengine.FitFixtures.striker;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * V3-07c 0a: yedek (depth) girişi rozetin türediği sayıyı taşır. Önceden yalnız ham rol puanı (roleFitScore = currentFit) + levelFit'ten gelen rozet
 * gidiyordu; ekran yüzdeyi ham puandan, rengi seviyeden gösteriyordu (%52 "Uygun"). Artık levelFit ve fitLabel de gelir; yüzde ve etiket aynı sayıdan.
 */
class DepthEntryLevelFitTest {

    @Test
    void depthEntryCarriesLevelFitAndLabelOfTheSameBasis() {
        FitPlayer starter = player(1, "İlk", "ST").all(80).accelerate(AcceleRate.EXPLOSIVE).build();
        FitPlayer backup = player(2, "Yedek", "ST").all(60).accelerate(AcceleRate.EXPLOSIVE).build();
        Map<Long, RoleFitResponse> fits = Map.of(
                1L, fit(1L, 75, 80.0, RoleFitV3Service.FitLabel.GOOD),
                2L, fit(2L, 52, 70.0, RoleFitV3Service.FitLabel.GOOD));
        RoleFitV3Service v3 = mock(RoleFitV3Service.class);
        when(v3.calculate(any(FitPlayer.class), any(FitRole.class), any(IntentProfile.class), anyString()))
                .thenAnswer((inv) -> fits.get(((FitPlayer) inv.getArgument(0)).getId()));
        SquadFitService service = new SquadFitService(ROLE_FIT, INTENT_FIT, HUNGARIAN, NLG, PROPERTIES, new com.fcareer.fitengine.service.SetupModifier(), v3);

        SquadFitResponse response = service.solve(List.of(starter, backup), List.of(slot("ST", "ST", "ST", striker())), FitEngineV3.VERSION);

        DepthEntry depth = response.getSlots().get(0).getDepth().get(0);
        assertThat(depth.getPlayerId()).isEqualTo(2L);
        assertThat(depth.getRoleFitScore()).isEqualTo(52);
        assertThat(depth.getLevelFit()).isEqualTo(70.0);
        assertThat(depth.getFitLabel()).isEqualTo("GOOD");
        assertThat(RoleFitV3Service.label(depth.getLevelFit(), PROPERTIES.getV3()).name()).isEqualTo(depth.getFitLabel());
    }

    private static RoleFitResponse fit(Long id, double score, Double levelFit, RoleFitV3Service.FitLabel label) {
        return RoleFitResponse.builder().playerId(id).playerName("p" + id).roleId("poacher_attack").roleName("Poacher").score(score).currentFit(score)
                .levelFit(levelFit).fitLabel(label.name()).badge(label.badge()).engineVersion(FitEngineV3.VERSION).outOfPosition(false).reasons(List.of()).build();
    }
}
