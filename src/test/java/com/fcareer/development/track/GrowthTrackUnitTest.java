package com.fcareer.development.track;

import com.fcareer.fitengine.model.Attr;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GTR-a: kariyer içi içe aktarmada export'taki bütün kulüplerin oyuncularından anlık görüntü (EA oyuncu kimliğiyle; transferde sabit). Kompakt
 * saklanır (gzip'li ikili), aynı oyun ayına düşen içe aktarmalardan yalnız sonuncusu, kariyer başına en çok 60; aşılırsa en eskiler seyreltilir.
 */
class GrowthTrackUnitTest {

    private static Map<String, String> row(Object... kv) {
        Map<String, String> out = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            out.put((String) kv[i], String.valueOf(kv[i + 1]));
        }
        return out;
    }

    /** 2000-01-01 doğumlu: oyun tarihinde (2027-09-19) 27 yaş. FC doğum tarihi gün sayısı (DumpMappings.BIRTHDATE_EPOCH_OFFSET). */
    private static final int BIRTH_2000 = (int) (LocalDate.of(2000, 1, 1).toEpochDay() + 141428L);

    @Test
    void snapshotHoldsEveryClubsPlayersWithPlayingFields() {
        List<Map<String, String>> players = List.of(
                row("playerid", 7, "overallrating", 70, "potential", 75, "birthdate", BIRTH_2000, "preferredposition1", 25, "finishing", 72, "sprintspeed", 80),
                row("playerid", 8, "overallrating", 64, "potential", 82, "birthdate", BIRTH_2000 + 365 * 8, "preferredposition1", 5, "finishing", 30),
                row("playerid", 9, "overallrating", 60, "potential", 60, "birthdate", BIRTH_2000, "preferredposition1", 0));
        List<Map<String, String>> links = List.of(
                row("playerid", 7, "teamid", 100, "form", 4, "leagueappearances", 12),
                row("playerid", 7, "teamid", 1355, "form", 3, "leagueappearances", 2),
                row("playerid", 8, "teamid", 200, "form", 2, "leagueappearances", 0));
        List<Map<String, String>> leagueLinks = List.of(row("teamid", 100, "nummatchesplayed", 14), row("teamid", 200, "nummatchesplayed", 9));
        List<Map<String, String>> ratings = List.of(row("playerid", 7, "rating", 70), row("playerid", 7, "rating", 65), row("playerid", 8, "rating", 60));
        Map<Long, Integer> playerTeam = Map.of(7L, 100, 8L, 200);

        List<TrackedPlayer> snapshot = GrowthSnapshotBuilder.build(LocalDate.of(2027, 9, 19), players, links, leagueLinks, ratings, playerTeam);

        assertThat(snapshot).extracting(TrackedPlayer::playerId).containsExactly(7L, 8L);
        TrackedPlayer st = snapshot.get(0);
        assertThat(st.age()).isEqualTo(27);
        assertThat(st.position()).isEqualTo("ST");
        assertThat(st.teamId()).as("kulüp bağı (milli takım değil)").isEqualTo(100);
        assertThat(st.form()).isEqualTo(4);
        assertThat(st.leagueApps()).isEqualTo(12);
        assertThat(st.teamMatches()).isEqualTo(14);
        assertThat(st.matches()).as("maç notu geçmişi satır sayısı").isEqualTo(2);
        assertThat(st.attr(Attr.FINISHING)).isEqualTo(72);
        assertThat(st.attr(Attr.SPRINT_SPEED)).isEqualTo(80);
        assertThat(snapshot.get(1).age()).isEqualTo(19);
        // Maç notu tablosu yoksa maç sayısı bilinmiyor (0 değil).
        assertThat(GrowthSnapshotBuilder.build(LocalDate.of(2027, 9, 19), players, links, leagueLinks, null, playerTeam).get(0).matches()).isNull();
        // Tabloda geçmeyen oyuncu da bilinmiyor: tablo (171 satır) yalnız kullanıcının kadrosunu kapsıyor olabilir; 0 yazılırsa "oynamadı" sanılır.
        assertThat(GrowthSnapshotBuilder.build(LocalDate.of(2027, 9, 19), players, links, leagueLinks, List.of(row("playerid", 8, "rating", 60)), playerTeam)
                .get(0).matches()).isNull();
    }

    @Test
    void aPlayerWithoutPositionOrBirthdateIsStillTracked() {
        List<TrackedPlayer> snapshot = GrowthSnapshotBuilder.build(LocalDate.of(2027, 9, 19), List.of(row("playerid", 7, "overallrating", 70)), List.of(),
                List.of(), null, Map.of(7L, 100));
        assertThat(snapshot).singleElement().satisfies((p) -> {
            assertThat(p.position()).isNull();
            assertThat(p.age()).isNull();
        });
    }

    @Test
    void theCodecRejectsValuesOutsideItsRange() {
        int[] attrs = TrackedPlayer.attrArray(Map.of());
        TrackedPlayer tooHigh = new TrackedPlayer(1, 1, 20, "ST", 130, 80, 3, 0, 0, null, attrs);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> GrowthSnapshotCodec.encode(List.of(tooHigh))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void codecRoundTripsAndStaysCompact() {
        List<TrackedPlayer> players = new ArrayList<>();
        for (int i = 0; i < 22_000; i++) {
            Map<Attr, Integer> attrs = new EnumMap<>(Attr.class);
            for (Attr a : Attr.values()) {
                attrs.put(a, 40 + (i + a.ordinal()) % 50);
            }
            players.add(new TrackedPlayer(100_000L + i, 1000 + i % 700, 18 + i % 20, i % 2 == 0 ? "ST" : "CB", 55 + i % 30, 60 + i % 30, i % 6,
                    i % 30, 30, i % 3 == 0 ? null : i % 30, TrackedPlayer.attrArray(attrs)));
        }
        byte[] bytes = GrowthSnapshotCodec.encode(players);
        assertThat(GrowthSnapshotCodec.decode(bytes)).isEqualTo(players);
        assertThat(bytes.length).as("22 bin oyuncu ≈ 1 MB altı").isLessThan(1_000_000);
    }

    /** Sınır: en çok 60. Aşılırsa en eskiler seyreltilir: ilk ve son 12 görüntü korunur, arada en sık aralıklı olanlardan biri silinir. */
    @Test
    void thinningKeepsTheCapAndThinsTheOldest() {
        List<GrowthTrackRepository.SnapshotRef> refs = new ArrayList<>();
        for (int m = 0; m < 61; m++) {
            refs.add(new GrowthTrackRepository.SnapshotRef(m + 1, LocalDate.of(2027, 1, 1).plusMonths(m)));
        }
        // Öncekine en yakın eski görüntü (14 gün): seyreltmede ilk o gider.
        refs.set(3, new GrowthTrackRepository.SnapshotRef(4, LocalDate.of(2027, 3, 15)));
        List<Long> drop = GrowthTrackService.toThin(refs, 60, 12);
        assertThat(drop).containsExactly(4L);
        assertThat(GrowthTrackService.toThin(refs.subList(0, 60), 60, 12)).isEmpty();
        List<Long> many = GrowthTrackService.toThin(refs, 50, 12);
        assertThat(many).hasSize(11).doesNotContain(1L).allMatch((id) -> id < 50);
    }

    @Test
    void playBucketsUseTheMatchRatio() {
        GrowthTrackProperties props = new GrowthTrackProperties();
        assertThat(props.getRegularFromRatio()).isEqualTo(0.5);
        assertThat(GrowthReport.playBucket(0, 10, props)).isEqualTo("NONE");
        assertThat(GrowthReport.playBucket(3, 10, props)).isEqualTo("LOW");
        assertThat(GrowthReport.playBucket(5, 10, props)).isEqualTo("REGULAR");
        assertThat(GrowthReport.playBucket(null, 10, props)).isEqualTo("UNKNOWN");
        assertThat(GrowthReport.playBucket(2, 0, props)).as("takım maçı yoksa bilinmiyor").isEqualTo("UNKNOWN");
    }
}
