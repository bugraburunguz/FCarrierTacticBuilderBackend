package com.fcareer.ut;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.ut.model.SbcModels.PoolEntry;
import com.fcareer.ut.model.SbcModels.Rarity;
import com.fcareer.ut.model.SbcModels.StreamlinedRequest;
import com.fcareer.ut.model.SbcModels.StreamlinedResult;
import com.fcareer.ut.service.SbcSolverService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SbcSolverServiceTest {

    private static SbcSolverService service;

    @BeforeAll
    static void setUp() throws Exception {
        service = new SbcSolverService(new ObjectMapper());
        Method load = SbcSolverService.class.getDeclaredMethod("load");
        load.setAccessible(true);
        load.invoke(service);
    }

    @Test
    void scoreTableMatchesSpec() {
        assertThat(service.scoreOf(99, Rarity.REGULAR)).isEqualTo(100000);
        assertThat(service.scoreOf(70, Rarity.REGULAR)).isEqualTo(35);
        assertThat(service.scoreOf(50, Rarity.REGULAR)).isEqualTo(20);
        assertThat(service.scoreOf(91, Rarity.TOTW)).isEqualTo(23750);
        assertThat(service.scoreOf(95, Rarity.ICON)).isEqualTo(60000);
        assertThat(service.scoreOf(87, Rarity.HERO)).isEqualTo(6875);
        assertThat(service.scoreOf(80, Rarity.HERO)).isEqualTo(180);
    }

    @Test
    void untradeableItemsAreFreeAndPreferred() {
        StreamlinedResult result = service.solveStreamlined(new StreamlinedRequest(360, null, List.of(
                new PoolEntry(80, Rarity.REGULAR, 5, true, null),
                new PoolEntry(80, Rarity.REGULAR, 5, false, 500L))));
        assertThat(result.feasible()).isTrue();
        assertThat(result.totalCost()).isZero();
        assertThat(result.totalScore()).isEqualTo(360);
        assertThat(result.picks()).hasSize(1);
        assertThat(result.picks().get(0).untradeable()).isTrue();
    }

    @Test
    void picksCheapestCombinationForTarget() {
        StreamlinedResult result = service.solveStreamlined(new StreamlinedRequest(360, null, List.of(
                new PoolEntry(83, Rarity.REGULAR, 2, false, 1000L),
                new PoolEntry(80, Rarity.REGULAR, 4, false, 200L))));
        assertThat(result.feasible()).isTrue();
        assertThat(result.totalCost()).isEqualTo(400);
        assertThat(result.overshoot()).isZero();
        assertThat(result.itemCount()).isEqualTo(2);
    }

    @Test
    void respectsMinRatingFilter() {
        StreamlinedResult result = service.solveStreamlined(new StreamlinedRequest(300, 83, List.of(
                new PoolEntry(80, Rarity.REGULAR, 10, true, null),
                new PoolEntry(83, Rarity.REGULAR, 1, true, null))));
        assertThat(result.feasible()).isTrue();
        assertThat(result.picks()).allMatch(p -> p.rating() >= 83);
    }

    @Test
    void reportsInfeasibleWhenPoolIsTooSmall() {
        StreamlinedResult result = service.solveStreamlined(new StreamlinedRequest(10000, null, List.of(
                new PoolEntry(80, Rarity.REGULAR, 2, true, null))));
        assertThat(result.feasible()).isFalse();
        assertThat(result.maxReachableScore()).isEqualTo(360);
    }

    @Test
    void coinlessModeMinimisesOvershoot() {
        StreamlinedResult result = service.solveStreamlined(new StreamlinedRequest(200, null, List.of(
                new PoolEntry(84, Rarity.REGULAR, 1, false, null),
                new PoolEntry(80, Rarity.REGULAR, 2, false, null))));
        assertThat(result.coinless()).isTrue();
        assertThat(result.totalScore()).isEqualTo(360);
    }
}
