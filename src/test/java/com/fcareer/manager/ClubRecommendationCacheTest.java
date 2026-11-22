package com.fcareer.manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.advisor.service.FeasibilityService;
import com.fcareer.advisor.service.FormationRanker;
import com.fcareer.advisor.service.PositionAdviceService;
import com.fcareer.catalog.service.PlayerService;
import com.fcareer.catalog.squad.SquadVersionRepository;
import com.fcareer.common.cache.ReferenceCache;
import com.fcareer.fitengine.configuration.FitEngineProperties;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.response.SquadFitResponse;
import com.fcareer.fitengine.service.SquadFitService;
import com.fcareer.manager.ManagerModels.Background;
import com.fcareer.manager.ManagerModels.Difficulty;
import com.fcareer.manager.ManagerModels.Licence;
import com.fcareer.manager.ManagerModels.ManagerProfile;
import com.fcareer.manager.ManagerModels.Priority;
import com.fcareer.manager.ManagerModels.RecommendationRequest;
import com.fcareer.manager.ManagerModels.Style;
import com.fcareer.tactics.service.FormationRegistry;
import com.fcareer.tactics.service.TacticResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClubRecommendationCacheTest {

    private static final short GAME = 27;
    private static final ManagerProfile PROFILE = new ManagerProfile("B", Licence.NATIONAL_A, Background.PRO_REGIONAL, "4-3-3", Style.COUNTER,
            List.of(Priority.YOUTH), Difficulty.MEDIUM, null, 0);

    private final ManagerRepository repository = mock(ManagerRepository.class);
    private final ManagerRules rules = mock(ManagerRules.class);
    private final PlayerService playerService = mock(PlayerService.class);
    private final SquadFitService squadFitService = mock(SquadFitService.class);
    private final TacticResolver tacticResolver = mock(TacticResolver.class);
    private final FormationRegistry formationRegistry = mock(FormationRegistry.class);
    private final ReferenceCache cache = new ReferenceCache(new ObjectMapper(), Clock.systemUTC());
    private ClubFitCalculator calculator;
    private ManagerService service;

    @BeforeEach
    void setUp() {
        calculator = new ClubFitCalculator(playerService, mock(FeasibilityService.class), mock(PositionAdviceService.class),
                mock(FormationRanker.class), formationRegistry, tacticResolver, squadFitService, new FitEngineProperties(), cache, repository);
        service = new ManagerService(repository, rules, new ManagerProperties(), calculator, playerService, mock(SquadVersionRepository.class), cache,
                Clock.systemUTC());
        when(playerService.resolveGameVersion(any())).thenReturn(GAME);
        com.fcareer.tactics.model.FormationDefinition formation = mock(com.fcareer.tactics.model.FormationDefinition.class);
        when(formation.id()).thenReturn("4-3-3");
        when(formationRegistry.findAll()).thenReturn(List.of(formation));
        when(repository.profileCount(anyShort(), isNull())).thenReturn(3);
        when(repository.catalogVersion(anyShort(), isNull())).thenReturn("v1");
        when(repository.clubStats(anyShort(), anyInt(), any(), isNull())).thenReturn(List.of(stat(1), stat(2), stat(3)));
        when(rules.needs(anyList())).thenReturn(Map.of(1L, 30, 2L, 30, 3L, 30));
        when(rules.pool(any(), anyInt(), anyInt(), anyInt())).thenReturn(List.of(1L, 2L, 3L));
        when(rules.select(anyList(), anyInt(), anyList(), any())).thenReturn(new ManagerRules.Selection(List.of(), List.of()));
        Map<Integer, List<Long>> squads = new LinkedHashMap<>();
        squads.put(1, ids(100));
        squads.put(2, ids(200));
        squads.put(3, ids(300));
        when(repository.catalogSquads(anyList(), eq(GAME), isNull())).thenReturn(squads);
        when(playerService.fitCatalogPlayers(anyList(), eq(GAME), isNull())).thenAnswer((inv) -> {
            List<Long> requested = inv.getArgument(0);
            return requested.stream().map((id) -> FitPlayer.builder().id(id).positions(List.of("CM")).attrs(Map.of()).playstyles(Map.of()).build()).toList();
        });
        when(tacticResolver.resolve(any())).thenReturn(new TacticResolver.ResolvedTactic("4-3-3", null, List.of()));
        when(squadFitService.solveBest(anyList(), anyList(), any())).thenReturn(SquadFitResponse.builder().squadFit(70.0).slots(new ArrayList<>()).build());
    }

    @Test
    void clubStatsAreReadOncePerCatalogVersionAndGender() {
        service.recommend(1L, new RecommendationRequest(PROFILE, null, null, null));
        service.recommend(1L, new RecommendationRequest(PROFILE, null, null, null));
        verify(repository, times(1)).clubStats(anyShort(), anyInt(), any(), isNull());

        service.recommend(1L, new RecommendationRequest(PROFILE, null, 1, null));
        verify(repository, times(2)).clubStats(anyShort(), anyInt(), any(), isNull());
    }

    @Test
    void poolSquadsAndPlayersAreLoadedInOneBatch() {
        service.recommend(1L, new RecommendationRequest(PROFILE, null, null, null));

        verify(repository, times(1)).catalogSquads(anyList(), eq(GAME), isNull());
        verify(playerService, times(1)).fitCatalogPlayers(anyList(), eq(GAME), isNull());
        verify(squadFitService, times(3)).solveBest(anyList(), anyList(), any());
        assertThat(calculator.computations()).isEqualTo(3);
    }

    @Test
    void cachedClubFitsAreNotLoadedAgain() {
        service.recommend(1L, new RecommendationRequest(PROFILE, null, null, null));
        service.recommend(1L, new RecommendationRequest(PROFILE, null, null, null));

        verify(repository, times(1)).catalogSquads(anyList(), eq(GAME), isNull());
        verify(playerService, times(1)).fitCatalogPlayers(anyList(), eq(GAME), isNull());
        assertThat(calculator.cacheStats().hits()).isEqualTo(3);
        assertThat(calculator.cacheStats().misses()).isEqualTo(3);
    }

    private static List<Long> ids(long from) {
        return LongStream.range(from, from + 11).boxed().toList();
    }

    private static ManagerRepository.ClubStat stat(long clubId) {
        return new ManagerRepository.ClubStat(clubId, "Club " + clubId, 1, "League", 5, 5, 1, 5, 1_000_000L, 18, 70.0, 10_000_000L, 0);
    }
}
