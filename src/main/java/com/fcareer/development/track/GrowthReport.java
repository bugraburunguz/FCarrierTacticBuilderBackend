package com.fcareer.development.track;

import com.fcareer.development.service.GrowthProjector;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * GTR-a ölçüm raporu: iki anlık görüntüde aynı EA kimliğine sahip oyuncular eşlenir (transfer olmuş oyuncu da). Gruplar (önceki görüntüye göre): yaş
 * bandı, oynama kovası (dönemde oyuncu maçı / takım maçı), yaş × oynama ve gençlerde (≤ 23) POT − OVR bandı. Her grupta n, ortalama ve medyan OVR
 * değişimi, fiziksel/teknik/zihinsel attribute ortalaması, GrowthProjector'ın plansız tahmini (sezona göre dönemle ölçekli) ve fark (gerçek − tahmin).
 * Dönem 365 günden kısaysa "kısmi dönem".
 */
public final class GrowthReport {

    private static final double DAYS_PER_SEASON = 365.0;
    private static final int YOUTH_MAX_AGE = 23;
    private static final int[][] AGE_BANDS = {{0, 19}, {20, 23}, {24, 29}, {30, 31}, {32, 33}, {34, 99}};
    private static final int[][] HEADROOM_BANDS = {{0, 4}, {5, 9}, {10, 14}, {15, 99}};
    private static final String UNKNOWN = "UNKNOWN";

    private GrowthReport() {
    }

    /** Grup satırı. meanPredicted/meanDiff: plansız model tahmini ve gerçek − tahmin (OVR). */
    public record Group(String key, int n, double meanOverallDelta, double medianOverallDelta, double physical, double technical, double mental,
                        double meanPredicted, double meanDiff) {
    }

    /** groups: boyut → gruplar ("age", "play", "agePlay", "headroom"). */
    public record Report(LocalDate from, LocalDate to, long days, boolean partial, int matched, Map<String, List<Group>> groups) {
    }

    private record Pair(TrackedPlayer before, TrackedPlayer after, double predicted, String play) {
        int delta() {
            return after.overall() - before.overall();
        }
    }

    public static String playBucket(Integer playerMatches, Integer teamMatches, GrowthTrackProperties properties) {
        if (Objects.isNull(playerMatches) || Objects.isNull(teamMatches) || teamMatches <= 0) {
            return UNKNOWN;
        }
        if (playerMatches <= 0) {
            return "NONE";
        }
        return (double) playerMatches / teamMatches < properties.getRegularFromRatio() ? "LOW" : "REGULAR";
    }

    public static Report build(List<TrackedPlayer> before, List<TrackedPlayer> after, LocalDate from, LocalDate to, GrowthProjector projector,
                               GrowthTrackProperties properties) {
        long days = ChronoUnit.DAYS.between(from, to);
        double fraction = days / DAYS_PER_SEASON;
        Map<Long, TrackedPlayer> earlier = before.stream().collect(Collectors.toMap(TrackedPlayer::playerId, Function.identity(), (a, b) -> a));
        List<Pair> pairs = new ArrayList<>();
        for (TrackedPlayer b : after) {
            TrackedPlayer a = earlier.get(b.playerId());
            if (Objects.isNull(a)) {
                continue;
            }
            pairs.add(new Pair(a, b, predicted(a, projector) * fraction, playBucket(periodCount(a.matches(), b.matches()), periodCount(a.teamMatches(), b.teamMatches()),
                    properties)));
        }
        Map<String, List<Group>> groups = new LinkedHashMap<>();
        groups.put("age", group(pairs, (p) -> ageBand(p.before().age())));
        groups.put("play", group(pairs, Pair::play));
        groups.put("agePlay", group(pairs, (p) -> ageBand(p.before().age()) + " · " + p.play()));
        groups.put("headroom", group(pairs.stream().filter((p) -> Objects.nonNull(p.before().age()) && p.before().age() <= YOUTH_MAX_AGE
                && Objects.nonNull(p.before().potential())).toList(), (p) -> headroomBand(p.before().potential() - p.before().overall())));
        return new Report(from, to, days, days < DAYS_PER_SEASON, pairs.size(), groups);
    }

    /** Dönemdeki sayı: sonraki − önceki; sezon başında sıfırlandıysa (negatif) sonraki değer. Biri bilinmiyorsa bilinmiyor. */
    private static Integer periodCount(Integer before, Integer after) {
        if (Objects.isNull(before) || Objects.isNull(after)) {
            return after;
        }
        return after >= before ? after - before : after;
    }

    /** Plansız bir sezonluk model tahmini (OVR değişimi); kaleci değilse saha attribute'larıyla. */
    private static double predicted(TrackedPlayer p, GrowthProjector projector) {
        Map<Attr, Integer> attrs = new EnumMap<>(Attr.class);
        for (Attr a : Attr.values()) {
            Integer v = p.attr(a);
            attrs.put(a, Objects.requireNonNullElse(v, 0));
        }
        FitPlayer fit = FitPlayer.builder().id(p.playerId()).age(p.age()).overall(p.overall()).potential(p.potential())
                .positions(Objects.isNull(p.position()) ? List.of() : List.of(p.position())).attrs(attrs).playstyles(Map.of()).gender(0).build();
        return projector.project(fit).projectedRating() - p.overall();
    }

    /** Grup sırası: yaş bandı ve oynama kovası doğal sırasıyla (metin sırası "≤19"u sona atardı), sonra metin. */
    private static final List<String> ORDER = List.of("≤19", "20-23", "24-29", "30-31", "32-33", "34+", "NONE", "LOW", "REGULAR", UNKNOWN);

    private static int rank(String key) {
        for (int i = 0; i < ORDER.size(); i++) {
            if (key.startsWith(ORDER.get(i))) {
                return i;
            }
        }
        return ORDER.size();
    }

    private static List<Group> group(List<Pair> pairs, Function<Pair, String> key) {
        Map<String, List<Pair>> byKey = new TreeMap<>(java.util.Comparator.comparingInt(GrowthReport::rank).thenComparing(Function.identity()));
        pairs.forEach((p) -> byKey.computeIfAbsent(key.apply(p), (k) -> new ArrayList<>()).add(p));
        List<Group> out = new ArrayList<>();
        byKey.forEach((k, list) -> {
            List<Integer> deltas = list.stream().map(Pair::delta).sorted().toList();
            double mean = deltas.stream().mapToInt(Integer::intValue).average().orElse(0);
            double median = deltas.size() % 2 == 1 ? deltas.get(deltas.size() / 2) : (deltas.get(deltas.size() / 2 - 1) + deltas.get(deltas.size() / 2)) / 2.0;
            double predicted = round(list.stream().mapToDouble(Pair::predicted).average().orElse(0));
            out.add(new Group(k, list.size(), round(mean), round(median), groupDelta(list, GrowthProjector.DeclineGroup.PHYSICAL),
                    groupDelta(list, GrowthProjector.DeclineGroup.TECHNICAL), groupDelta(list, GrowthProjector.DeclineGroup.MENTAL), predicted,
                    round(round(mean) - predicted)));
        });
        return out;
    }

    /** Gruptaki attribute'ların oyuncu başına ortalama değişimi (kaleci attribute'ları hariç; iki görüntüde de bilinenler). */
    private static double groupDelta(List<Pair> pairs, GrowthProjector.DeclineGroup group) {
        return round(pairs.stream().mapToDouble((p) -> {
            List<Integer> d = new ArrayList<>();
            for (Attr a : Attr.values()) {
                if (a.name().startsWith("GK_") || GrowthProjector.group(a) != group) {
                    continue;
                }
                Integer x = p.before().attr(a);
                Integer y = p.after().attr(a);
                if (Objects.nonNull(x) && Objects.nonNull(y)) {
                    d.add(y - x);
                }
            }
            return d.stream().mapToInt(Integer::intValue).average().orElse(0);
        }).average().orElse(0));
    }

    private static String ageBand(Integer age) {
        if (Objects.isNull(age)) {
            return UNKNOWN;
        }
        for (int[] band : AGE_BANDS) {
            if (age >= band[0] && age <= band[1]) {
                return band[0] == 0 ? "≤" + band[1] : band[1] == 99 ? band[0] + "+" : band[0] + "-" + band[1];
            }
        }
        return UNKNOWN;
    }

    private static String headroomBand(int headroom) {
        for (int[] band : HEADROOM_BANDS) {
            if (headroom >= band[0] && headroom <= band[1]) {
                return band[1] == 99 ? "POT−OVR ≥" + band[0] : band[0] == 0 ? "POT−OVR <5" : "POT−OVR " + band[0] + "-" + band[1];
            }
        }
        return "POT−OVR <5";
    }

    /** CSV (virgülle, nokta ondalık): boyut, grup, n, ortalama ve medyan OVR, attribute grupları, tahmin, fark. */
    public static String csv(Report report) {
        StringBuilder out = new StringBuilder("boyut,grup,n,ortalama_ovr,medyan_ovr,fiziksel,teknik,zihinsel,tahmin,fark\n");
        report.groups().forEach((dimension, groups) -> groups.forEach((g) -> out.append(String.format(Locale.ROOT, "%s,%s,%d,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f%n",
                dimension, g.key(), g.n(), g.meanOverallDelta(), g.medianOverallDelta(), g.physical(), g.technical(), g.mental(), g.meanPredicted(), g.meanDiff()))));
        return out.toString();
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
