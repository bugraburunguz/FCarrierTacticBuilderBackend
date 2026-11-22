package com.fcareer.fitengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.billing.configuration.BillingProperties;
import com.fcareer.billing.service.CreditGuard;
import com.fcareer.career.service.CareerService;
import com.fcareer.catalog.service.PlayerService;
import com.fcareer.common.exception.NotFoundException;
import com.fcareer.fitengine.configuration.FitEngineProperties;
import com.fcareer.fitengine.level.FitLevelNormsRepository;
import com.fcareer.fitengine.level.LevelFitService;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.IntentProfile;
import com.fcareer.fitengine.model.request.SquadFitRequest;
import com.fcareer.fitengine.model.response.SquadFitResponse;
import com.fcareer.fitengine.service.FitService;
import com.fcareer.fitengine.service.LineupSolveCache;
import com.fcareer.fitengine.service.SquadFitService;
import com.fcareer.fitengine.service.WeakLinkClassifier;
import com.fcareer.tacticanalysis.SquadFitRuleAdapter;
import com.fcareer.tactics.model.request.TacticRequest;
import com.fcareer.tactics.service.TacticResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LineupSolveCacheTest {

    private static final long OWNER = 1L;
    private static final long STRANGER = 2L;
    private static final long CAREER = 40L;

    private final CareerService careerService = mock(CareerService.class);
    private final PlayerService playerService = mock(PlayerService.class);
    private final TacticResolver tacticResolver = mock(TacticResolver.class);
    private final SquadFitService squadFitService = mock(SquadFitService.class);
    private final WeakLinkClassifier weakLinkClassifier = mock(WeakLinkClassifier.class);
    private final CreditGuard creditGuard = mock(CreditGuard.class);
    private final MutableClock clock = new MutableClock();
    private LineupSolveCache cache;
    private FitService fitService;

    @BeforeEach
    void setUp() {
        cache = new LineupSolveCache(new ObjectMapper(), clock);
        fitService = new FitService(playerService, null, tacticResolver, mock(SquadFitRuleAdapter.class), careerService, null, null, squadFitService, creditGuard,
                new BillingProperties(), null, new FitEngineProperties(), weakLinkClassifier, null, cache);
        when(careerService.squadPlayerIds(OWNER, CAREER)).thenReturn(new CareerService.CareerSquad(List.of(7L, 8L), (short) 27, null, (short) 0, 10, CAREER, List.of(7L, 8L)));
        when(careerService.squadPlayerIds(STRANGER, CAREER)).thenThrow(new NotFoundException("career", CAREER));
        when(playerService.fitPlayers(anyList(), any(), eq(CAREER))).thenReturn(squad(70));
        when(tacticResolver.resolve(any())).thenAnswer(inv -> new TacticResolver.ResolvedTactic(((TacticRequest) inv.getArgument(0)).getFormation(), null,
                List.of(IntentProfile.builder().slotId("ST").slotPosition("ST").slotGroup(((TacticRequest) inv.getArgument(0)).getFormation()).build())));
        when(squadFitService.solveBest(anyList(), anyList(), any(), any())).thenAnswer(inv -> SquadFitResponse.builder().squadFit(70).slots(new ArrayList<>()).bench(new ArrayList<>(List.of(7L))).build());
        when(weakLinkClassifier.classify(anyList())).thenReturn(Map.of());
    }

    @Test
    void theSameRequestTwiceSolvesOnce() {
        SquadFitResponse first = fitService.lineup(OWNER, request("4-4-2", null));
        SquadFitResponse second = fitService.lineup(OWNER, request("4-4-2", null));

        verify(squadFitService, times(1)).solveBest(anyList(), anyList(), any(), any());
        assertThat(second.getSquadFit()).isEqualTo(first.getSquadFit());
        assertThat(second).isNotSameAs(first);
        assertThat(cache.stats().hits()).isEqualTo(1);
        assertThat(cache.stats().misses()).isEqualTo(1);
    }

    @Test
    void cacheStatsLogEveryInterval() {
        com.fcareer.common.cache.CacheStats stats = new com.fcareer.common.cache.CacheStats("test-cache", 2);

        assertThat(stats.record(true)).isFalse();
        assertThat(stats.record(false)).isTrue();
        assertThat(stats.record(true)).isFalse();
        assertThat(stats.hits()).isEqualTo(2);
        assertThat(stats.misses()).isEqualTo(1);
    }

    @Test
    void aChangedSquadOrRatingSolvesAgain() {
        fitService.lineup(OWNER, request("4-4-2", null));
        when(playerService.fitPlayers(anyList(), any(), eq(CAREER))).thenReturn(squad(71));

        fitService.lineup(OWNER, request("4-4-2", null));

        verify(squadFitService, times(2)).solveBest(anyList(), anyList(), any(), any());
    }

    @Test
    void aDifferentLockOrTacticIsADifferentKey() {
        fitService.lineup(OWNER, request("4-4-2", null));
        fitService.lineup(OWNER, request("4-4-2", Map.of("ST", 7L)));
        fitService.lineup(OWNER, request("4-3-3", null));

        verify(squadFitService, times(3)).solveBest(anyList(), anyList(), any(), any());
    }

    @Test
    void anotherUsersCareerIsRejectedBeforeTheCache() {
        fitService.lineup(OWNER, request("4-4-2", null));

        assertThatThrownBy(() -> fitService.lineup(STRANGER, request("4-4-2", null))).isInstanceOf(NotFoundException.class);
        verify(squadFitService, times(1)).solveBest(anyList(), anyList(), any(), any());
    }

    @Test
    void anEntryExpiresAfterItsTtl() {
        fitService.lineup(OWNER, request("4-4-2", null));
        clock.advance(LineupSolveCache.TTL.plusSeconds(1));

        fitService.lineup(OWNER, request("4-4-2", null));

        verify(squadFitService, times(2)).solveBest(anyList(), anyList(), any(), any());
    }

    @Test
    void theCacheKeepsAtMostItsMaximumEntries() {
        for (int i = 0; i <= LineupSolveCache.MAX_ENTRIES; i++) {
            cache.getOrSolve(i, List.of(), List.of(), "v3", null, () -> new SquadFitResponse());
        }

        assertThat(cache.size()).isEqualTo(LineupSolveCache.MAX_ENTRIES);
    }

    @Test
    void refreshingLevelNormsClearsCachedSolves() {
        fitService.lineup(OWNER, request("4-4-2", null));

        new LevelFitService(mock(FitLevelNormsRepository.class), cache).refresh();
        fitService.lineup(OWNER, request("4-4-2", null));

        verify(squadFitService, times(2)).solveBest(anyList(), anyList(), any(), any());
    }

    @Test
    void theCachedSolveIsNotChangedByTheFreeResponse() {
        SquadFitResponse first = fitService.lineup(OWNER, request("4-4-2", null));

        assertThatThrownBy(() -> first.getBench().add(99L)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void squadFitReadsTheCareerSquadOnce() {
        fitService.squadFit(OWNER, SquadFitRequest.builder().careerId(CAREER).tactic(TacticRequest.builder().formation("4-4-2").build()).build());

        verify(careerService, times(1)).squadPlayerIds(anyLong(), anyLong());
        verify(squadFitService, never()).solveBest(anyList(), anyList(), any(), any());
    }

    private static SquadFitRequest request(String formation, Map<String, Long> locked) {
        return SquadFitRequest.builder().careerId(CAREER).tactic(TacticRequest.builder().formation(formation).build()).locked(locked).build();
    }

    private static List<FitPlayer> squad(int overall) {
        Map<Attr, Integer> attrs = new EnumMap<>(Attr.class);
        attrs.put(Attr.values()[0], overall);
        return List.of(FitPlayer.builder().id(7L).name("A").overall(overall).positions(List.of("ST")).attrs(attrs).playstyles(Map.of()).build(),
                FitPlayer.builder().id(8L).name("B").overall(65).positions(List.of("CB")).attrs(Map.of()).playstyles(Map.of()).build());
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-22T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
