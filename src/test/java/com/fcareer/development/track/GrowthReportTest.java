package com.fcareer.development.track;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.catalog.service.PositionOverallFixture;
import com.fcareer.development.service.GrowthProjector;
import com.fcareer.fitengine.model.Attr;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * GTR-a: iki anlık görüntü arasında aynı oyuncular (EA kimliği; transfer olmuş oyuncu da) eşlenir. Gruplar: yaş bandı, oynama kovası, gençlerde
 * POT − OVR bandı. Her grupta n, ortalama ve medyan OVR değişimi, attribute grubu ortalamaları ve GrowthProjector'ın plansız tahminiyle fark.
 */
class GrowthReportTest {

    private final GrowthProjector projector = new GrowthProjector(new GrowthProjector.Config(), PositionOverallFixture.loaded(new ObjectMapper()));
    private final GrowthTrackProperties props = new GrowthTrackProperties();

    private static TrackedPlayer p(long id, int team, int age, int ovr, int pot, Integer matches, Integer teamMatches, int attr) {
        Map<Attr, Integer> attrs = new EnumMap<>(Attr.class);
        for (Attr a : Attr.values()) {
            attrs.put(a, attr);
        }
        return new TrackedPlayer(id, team, age, "ST", ovr, pot, 3, 0, teamMatches, matches, TrackedPlayer.attrArray(attrs));
    }

    @Test
    void matchesPlayersAcrossSnapshotsAndGroupsThem() {
        List<TrackedPlayer> before = List.of(p(1, 10, 18, 60, 80, 0, 0, 60), p(2, 10, 18, 61, 80, 0, 0, 60), p(3, 20, 33, 80, 80, 0, 0, 70), p(4, 20, 26, 75, 77, 0, 0, 70));
        List<TrackedPlayer> after = List.of(
                p(1, 10, 19, 66, 80, 30, 34, 66),
                p(2, 99, 19, 63, 80, 4, 34, 62),     // transfer: kulübü değişti, yine eşlenir
                p(3, 20, 34, 78, 78, 20, 34, 68),
                p(5, 20, 20, 60, 70, 0, 34, 60));    // yeni oyuncu: eşlenmez
        GrowthReport.Report report = GrowthReport.build(before, after, LocalDate.of(2027, 7, 1), LocalDate.of(2028, 7, 1), projector, props);

        assertThat(report.matched()).isEqualTo(3);
        assertThat(report.partial()).as("bir sezon").isFalse();
        GrowthReport.Group youngRegular = group(report, "agePlay", "≤19 · REGULAR");
        assertThat(youngRegular.n()).isEqualTo(1);
        assertThat(youngRegular.meanOverallDelta()).isEqualTo(6.0);
        assertThat(group(report, "agePlay", "≤19 · LOW").meanOverallDelta()).isEqualTo(2.0);
        GrowthReport.Group young = group(report, "age", "≤19");
        assertThat(young.n()).isEqualTo(2);
        assertThat(young.medianOverallDelta()).isEqualTo(4.0);
        assertThat(young.physical()).isCloseTo(4.0, within(1e-9));
        assertThat(group(report, "age", "32-33").meanOverallDelta()).isEqualTo(-2.0);
        assertThat(group(report, "headroom", "POT−OVR ≥15").n()).as("≤ 23 yaş, boşluk 19-20").isEqualTo(2);
        assertThat(report.groups().get("headroom")).noneMatch((g) -> g.n() > 2);
        // Model kıyası: plansız tahmin ve fark (gerçek − tahmin) aynı oyuncular için.
        GrowthReport.Group veteran = group(report, "age", "32-33");
        assertThat(veteran.meanPredicted()).isLessThan(0);
        assertThat(veteran.meanDiff()).isCloseTo(veteran.meanOverallDelta() - veteran.meanPredicted(), within(1e-9));
        // Oynama kovası grupları ayrıca.
        assertThat(report.groups().get("play")).extracting(GrowthReport.Group::key).contains("REGULAR", "LOW");
    }

    @Test
    void aPeriodShorterThanASeasonIsPartialAndThePredictionScales() {
        List<TrackedPlayer> before = List.of(p(1, 10, 18, 60, 80, 0, 0, 60));
        List<TrackedPlayer> after = List.of(p(1, 10, 18, 62, 80, 10, 12, 61));
        GrowthReport.Report full = GrowthReport.build(before, after, LocalDate.of(2027, 7, 1), LocalDate.of(2028, 7, 1), projector, props);
        GrowthReport.Report half = GrowthReport.build(before, after, LocalDate.of(2027, 7, 1), LocalDate.of(2027, 12, 30), projector, props);
        assertThat(half.partial()).isTrue();
        assertThat(half.days()).isEqualTo(182);
        assertThat(group(half, "age", "≤19").meanPredicted()).isCloseTo(group(full, "age", "≤19").meanPredicted() * 182 / 366.0, within(0.05));
    }

    /**
     * Oynama dönemi ara görüntüler üzerinden toplanır: lig maç sayaçları sezon başında sıfırlanır (sonraki < önceki → sonraki değer), takım değiştiren
     * oyuncuda takım sayacı yeni takımınki. Bir uçta sayı bilinmiyorsa (eski betik) o adım bilinmiyor; oran şişmez.
     */
    @Test
    void playIsSummedOverTheChainAcrossSeasonResetsAndUnknownStaysUnknown() {
        TrackedPlayer start = p(1, 10, 25, 70, 72, 20, 30, 70);
        TrackedPlayer seasonEnd = p(1, 10, 25, 70, 72, 34, 38, 70);
        TrackedPlayer nextSeason = p(1, 10, 26, 71, 72, 3, 4, 71);
        GrowthReport.Report chain = GrowthReport.build(List.of(
                new GrowthReport.Dated(LocalDate.of(2027, 3, 1), List.of(start)),
                new GrowthReport.Dated(LocalDate.of(2027, 6, 1), List.of(seasonEnd)),
                new GrowthReport.Dated(LocalDate.of(2027, 9, 1), List.of(nextSeason))), projector, props);
        // 14 + 3 = 17 maç / 8 + 4 = 12 takım maçı → düzenli.
        assertThat(chain.groups().get("play")).extracting(GrowthReport.Group::key).containsExactly("REGULAR");
        assertThat(GrowthReport.periodPlay(List.of(List.of(start), List.of(seasonEnd), List.of(nextSeason))).get(1L)).containsExactly(17, 12);
        // Takım değişimi: yeni takımın sayacı.
        TrackedPlayer moved = p(1, 99, 25, 70, 72, 25, 9, 70);
        assertThat(GrowthReport.periodPlay(List.of(List.of(start), List.of(moved))).get(1L)).containsExactly(5, 9);
        // Bir uç bilinmiyorsa (2.0.3 betiği: matches null) bilinmiyor.
        TrackedPlayer old = p(1, 10, 25, 70, 72, null, 30, 70);
        assertThat(GrowthReport.periodPlay(List.of(List.of(old), List.of(seasonEnd))).get(1L)).isNull();
        assertThat(GrowthReport.build(List.of(old), List.of(seasonEnd), LocalDate.of(2027, 3, 1), LocalDate.of(2027, 6, 1), projector, props)
                .groups().get("play")).extracting(GrowthReport.Group::key).containsExactly("UNKNOWN");
    }

    @Test
    void csvHasOneRowPerGroup() {
        List<TrackedPlayer> before = List.of(p(1, 10, 18, 60, 80, 0, 0, 60));
        List<TrackedPlayer> after = List.of(p(1, 10, 19, 64, 80, 20, 30, 63));
        String csv = GrowthReport.csv(GrowthReport.build(before, after, LocalDate.of(2027, 7, 1), LocalDate.of(2028, 7, 1), projector, props));
        assertThat(csv.lines().findFirst()).contains("boyut,grup,n,ortalama_ovr,medyan_ovr,fiziksel,teknik,zihinsel,tahmin,fark");
        assertThat(csv).contains("age,≤19,1,4.00,4.00");
    }

    private static GrowthReport.Group group(GrowthReport.Report report, String dimension, String key) {
        return report.groups().get(dimension).stream().filter((g) -> g.key().equals(key)).findFirst().orElseThrow();
    }
}
