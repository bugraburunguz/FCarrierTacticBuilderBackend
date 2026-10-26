package com.fcareer.development.track;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** GTR-b: Gelişim satırı "Son {N} ayda: +3 OVR · 18 maç" hesabı (taban görüntü seçimi, OVR farkı, dönem maçı). */
class GrowthRecentTest {

    private static final LocalDate NOW = LocalDate.of(2027, 9, 19);

    private static TrackedPlayer player(long id, int overall, Integer teamMatches, Integer matches) {
        return new TrackedPlayer(id, 1, 22, "ST", overall, 80, 3, 0, teamMatches, matches, new int[0]);
    }

    @Test
    void theBaseIsTheOldestSnapshotInsideTheWindow() {
        List<LocalDate> dates = List.of(NOW.minusMonths(9), NOW.minusMonths(6), NOW.minusMonths(4), NOW.minusMonths(1), NOW);
        assertThat(GrowthRecent.baseIndex(dates, 6)).isEqualTo(1);
        assertThat(GrowthRecent.baseIndex(dates, 3)).isEqualTo(3);
    }

    @Test
    void noBaseWithOneSnapshotOrOnlyOlderOnes() {
        assertThat(GrowthRecent.baseIndex(List.of(NOW), 6)).isEqualTo(-1);
        assertThat(GrowthRecent.baseIndex(List.of(NOW.minusMonths(8), NOW), 6)).isEqualTo(-1);
        assertThat(GrowthRecent.baseIndex(List.of(), 6)).isEqualTo(-1);
    }

    @Test
    void deltaAndMatchesAcrossTheChain() {
        List<GrowthReport.Dated> chain = List.of(
                new GrowthReport.Dated(NOW.minusMonths(3), List.of(player(10, 70, 4, 3), player(11, 80, null, null))),
                new GrowthReport.Dated(NOW.minusMonths(1), List.of(player(10, 72, 12, 10), player(11, 79, null, null))),
                new GrowthReport.Dated(NOW, List.of(player(10, 73, 20, 18), player(11, 78, null, null), player(12, 60, 20, 5))));
        Map<Long, GrowthRecent.Change> out = GrowthRecent.compute(chain);
        assertThat(out.get(10L)).isEqualTo(new GrowthRecent.Change(3, 3, 15));
        assertThat(out.get(11L)).as("maç verisi yok: yalnız OVR").isEqualTo(new GrowthRecent.Change(3, -2, null));
        assertThat(out).as("tabanda olmayan oyuncu (sonradan gelen) satır almaz").doesNotContainKey(12L);
    }

    @Test
    void underAMonthCountsAsOneMonth() {
        List<GrowthReport.Dated> chain = List.of(new GrowthReport.Dated(LocalDate.of(2027, 8, 31), List.of(player(10, 70, null, null))),
                new GrowthReport.Dated(LocalDate.of(2027, 9, 2), List.of(player(10, 70, null, null))));
        assertThat(GrowthRecent.compute(chain).get(10L).months()).isEqualTo(1);
    }

    @Test
    void monthsRoundToTheNearestMonth() {
        List<GrowthReport.Dated> chain = List.of(new GrowthReport.Dated(LocalDate.of(2027, 3, 20), List.of(player(10, 70, null, null))),
                new GrowthReport.Dated(LocalDate.of(2027, 9, 19), List.of(player(10, 70, null, null))));
        assertThat(GrowthRecent.compute(chain).get(10L).months()).as("6 aya bir gün eksik: 6, 5 değil").isEqualTo(6);
    }

    @Test
    void onlyTheRequestedPlayersAreKept() {
        List<GrowthReport.Dated> chain = List.of(new GrowthReport.Dated(NOW.minusMonths(3), List.of(player(10, 70, 4, 3), player(11, 80, 4, 4))),
                new GrowthReport.Dated(NOW, List.of(player(10, 73, 20, 18), player(11, 79, 20, 10))));
        assertThat(GrowthRecent.only(chain, java.util.Set.of(10L)).get(1).players()).extracting(TrackedPlayer::playerId).containsExactly(10L);
        assertThat(GrowthRecent.compute(GrowthRecent.only(chain, java.util.Set.of(10L)))).containsOnlyKeys(10L);
    }
}
