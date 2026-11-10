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
import static com.fcareer.fitengine.FitFixtures.classic10;
import static com.fcareer.fitengine.FitFixtures.holding;
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

    @Test
    void aMultiPositionBackupCoversOnlyOneSlotAndEachSlotGetsItsBestAvailableBackup() {
        List<FitPlayer> squad = List.of(adult(1, "CM"), adult(2, "CDM"), adult(3, "CAM"),
                adult(4, "CM", "CDM", "CAM"), adult(5, "CDM"), adult(6, "CAM"));
        Map<Long, Map<String, Double>> score = Map.of(
                1L, Map.of("CM", 95.0), 2L, Map.of("CDM", 95.0), 3L, Map.of("CAM", 95.0),
                4L, Map.of("CM", 80.0, "CDM", 78.0, "CAM", 77.0), 5L, Map.of("CDM", 70.0), 6L, Map.of("CAM", 70.0));
        SquadFitService service = serviceWith((p, position) -> {
            double s = score.get(p.getId()).getOrDefault(position, 40.0);
            return fit(p.getId(), s, s, RoleFitV3Service.FitLabel.GOOD);
        });

        SquadFitResponse response = service.solve(squad, List.of(slot("CM", "CM", "CM", holding()), slot("CDM", "CDM", "CDM", holding()),
                slot("CAM", "CAM", "CAM", classic10())), FitEngineV3.VERSION);

        Map<String, List<Long>> backups = new java.util.HashMap<>();
        response.getSlots().forEach((s) -> backups.put(s.getSlotId(), s.getDepth().stream().map(DepthEntry::getPlayerId).toList()));
        assertThat(backups).as("çok mevkili 4 yalnız en iyi olduğu CM'de; diğer slotlar kendi en iyi yedeğini alır")
                .containsEntry("CM", List.of(4L)).containsEntry("CDM", List.of(5L)).containsEntry("CAM", List.of(6L));
        response.getSlots().forEach((s) -> assertThat(s.getDepth()).allSatisfy((d) -> assertThat(d.isWeak()).isFalse()));
    }

    @Test
    void goalkeepersBackUpOnlyTheGoalAndYouthStaysOutOfTheBackupColumn() {
        List<FitPlayer> squad = List.of(adult(1, "GK"), adult(2, "ST"), adult(3, "GK"), adult(4, "ST").toBuilder().age(20).build());
        Map<Long, Map<String, Double>> score = Map.of(1L, Map.of("GK", 95.0), 2L, Map.of("ST", 95.0), 3L, Map.of("GK", 60.0, "ST", 90.0), 4L, Map.of("ST", 85.0));
        SquadFitService service = serviceWith((p, position) -> {
            double s = score.get(p.getId()).getOrDefault(position, 10.0);
            return fit(p.getId(), s, s, RoleFitV3Service.FitLabel.GOOD);
        });

        SquadFitResponse response = service.solve(squad, List.of(slot("GK", "GK", "GK", striker()), slot("ST", "ST", "ST", striker())), FitEngineV3.VERSION);

        Map<String, List<Long>> backups = new java.util.HashMap<>();
        response.getSlots().forEach((s) -> backups.put(s.getSlotId(), s.getDepth().stream().map(DepthEntry::getPlayerId).toList()));
        assertThat(backups.get("GK")).containsExactly(3L);
        assertThat(backups.get("ST")).as("kaleci forvet yedeği olmaz, 20 yaşındaki Genç kolonunda").isEmpty();
    }

    @Test
    void aWeakBackupIsFlaggedWithItsStrength() {
        List<FitPlayer> squad = List.of(adult(1, "ST"), adult(2, "ST"));
        Map<Long, RoleFitResponse> fits = Map.of(1L, fit(1L, 80, 80.0, RoleFitV3Service.FitLabel.GOOD), 2L, fit(2L, 41, 35.0, RoleFitV3Service.FitLabel.WEAK));
        SquadFitService service = serviceWith((p, position) -> fits.get(p.getId()));

        DepthEntry backup = service.solve(squad, List.of(slot("ST", "ST", "ST", striker())), FitEngineV3.VERSION).getSlots().get(0).getDepth().get(0);

        assertThat(backup.getPlayerId()).isEqualTo(2L);
        assertThat(backup.isWeak()).isTrue();
        assertThat(backup.getCurrentFit()).isEqualTo(41.0);
    }

    private static FitPlayer adult(long id, String... positions) {
        return player(id, "p" + id, positions).all(70).accelerate(AcceleRate.CONTROLLED).build().toBuilder().age(27).build();
    }

    private static SquadFitService serviceWith(java.util.function.BiFunction<FitPlayer, String, RoleFitResponse> fits) {
        RoleFitV3Service v3 = mock(RoleFitV3Service.class);
        when(v3.calculate(any(FitPlayer.class), any(FitRole.class), any(IntentProfile.class), anyString()))
                .thenAnswer((inv) -> fits.apply(inv.getArgument(0), inv.getArgument(3)));
        return new SquadFitService(ROLE_FIT, INTENT_FIT, HUNGARIAN, NLG, PROPERTIES, new com.fcareer.fitengine.service.SetupModifier(), v3);
    }

    private static RoleFitResponse fit(Long id, double score, Double levelFit, RoleFitV3Service.FitLabel label) {
        return RoleFitResponse.builder().playerId(id).playerName("p" + id).roleId("poacher_attack").roleName("Poacher").score(score).currentFit(score)
                .levelFit(levelFit).fitLabel(label.name()).badge(label.badge()).engineVersion(FitEngineV3.VERSION).outOfPosition(false).reasons(List.of()).build();
    }
}
