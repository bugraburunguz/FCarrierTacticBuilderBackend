package com.fcareer.development.calibration;

import com.fcareer.fitengine.model.Attr;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * FIT-3 madde 10: büyüme modeli kalibrasyonu, ardışık iki IMPORT anlık görüntüsünden. Yalnız ölçüm; katsayılar otomatik değişmez.
 * Ölçülen: OVR ve attribute başına ortalama artış, yaş bandına göre OVR artışı, hız grubu / diğerleri oranı (modeldeki 0,3× ile kıyas).
 */
public final class GrowthCalibration {

    private static final Set<Attr> HARD_TO_TRAIN = Set.of(Attr.ACCELERATION, Attr.SPRINT_SPEED, Attr.AGILITY, Attr.BALANCE, Attr.JUMPING);
    private static final Set<Attr> GOALKEEPING = Set.of(Attr.GK_DIVING, Attr.GK_HANDLING, Attr.GK_KICKING, Attr.GK_POSITIONING, Attr.GK_REFLEXES);
    private static final double ROUND = 100.0;
    private static final String NO_DATA = "veri yok: kariyer içinden en az iki içe aktarma (attribute'lu) gerekir";
    /** Yaş bantları (üst sınır dahil); sonuncusu açık uçlu. */
    private static final int[][] BANDS = {{0, 19}, {20, 21}, {22, 23}, {24, 25}, {26, 27}, {28, 99}};

    private GrowthCalibration() {
    }

    public record SnapshotPlayer(long playerId, Integer age, Integer overall, Map<Attr, Integer> attrs) {
    }

    public record AttributeGain(Attr attr, double meanDelta) {
    }

    public record AgeBand(String label, int players, double meanOverallDelta) {
    }

    public record Calibration(String status, String message, Long fromSnapshot, Long toSnapshot, int players, int growingPlayers, double meanOverallDelta,
                              List<AttributeGain> attributes, List<AgeBand> ageBands, Double hardToTrainRatio, double modelHardToTrain) {
    }

    public static Calibration noData() {
        return new Calibration("NO_DATA", NO_DATA, null, null, 0, 0, 0, List.of(), List.of(), null, 0);
    }

    public static Calibration summarize(List<SnapshotPlayer> before, List<SnapshotPlayer> after, double modelHardToTrain) {
        Map<Long, SnapshotPlayer> earlier = before.stream().collect(Collectors.toMap(SnapshotPlayer::playerId, Function.identity(), (a, b) -> a));
        List<SnapshotPlayer[]> pairs = after.stream().filter((p) -> earlier.containsKey(p.playerId()) && Objects.nonNull(p.overall())
                        && Objects.nonNull(earlier.get(p.playerId()).overall()))
                .map((p) -> new SnapshotPlayer[]{earlier.get(p.playerId()), p}).toList();
        if (pairs.isEmpty()) {
            return noData();
        }
        double meanOvr = pairs.stream().mapToInt((x) -> x[1].overall() - x[0].overall()).average().orElse(0);
        int growing = (int) pairs.stream().filter((x) -> x[1].overall() > x[0].overall()).count();
        List<AttributeGain> gains = new ArrayList<>();
        for (Attr attr : Attr.values()) {
            if (GOALKEEPING.contains(attr)) {
                continue;
            }
            pairs.stream().filter((x) -> x[0].attrs().containsKey(attr) && x[1].attrs().containsKey(attr))
                    .mapToInt((x) -> x[1].attrs().get(attr) - x[0].attrs().get(attr)).average()
                    .ifPresent((m) -> gains.add(new AttributeGain(attr, round(m))));
        }
        gains.sort(Comparator.comparingDouble(AttributeGain::meanDelta).reversed().thenComparing(AttributeGain::attr));
        double hard = gains.stream().filter((g) -> HARD_TO_TRAIN.contains(g.attr())).mapToDouble(AttributeGain::meanDelta).average().orElse(0);
        double other = gains.stream().filter((g) -> !HARD_TO_TRAIN.contains(g.attr())).mapToDouble(AttributeGain::meanDelta).average().orElse(0);
        List<AgeBand> bands = new ArrayList<>();
        for (int[] band : BANDS) {
            List<SnapshotPlayer[]> inBand = pairs.stream().filter((x) -> Objects.nonNull(x[0].age()) && x[0].age() >= band[0] && x[0].age() <= band[1]).toList();
            if (!inBand.isEmpty()) {
                String label = band[0] == 0 ? "≤" + band[1] : band[1] == 99 ? band[0] + "+" : band[0] + "-" + band[1];
                bands.add(new AgeBand(label, inBand.size(), round(inBand.stream().mapToInt((x) -> x[1].overall() - x[0].overall()).average().orElse(0))));
            }
        }
        return new Calibration("DATA", null, null, null, pairs.size(), growing, round(meanOvr), List.copyOf(gains), List.copyOf(bands),
                other > 0 ? round(hard / other) : null, modelHardToTrain);
    }

    public static Calibration withSnapshots(Calibration c, long from, long to) {
        return new Calibration(c.status(), c.message(), from, to, c.players(), c.growingPlayers(), c.meanOverallDelta(), c.attributes(), c.ageBands(),
                c.hardToTrainRatio(), c.modelHardToTrain());
    }

    private static double round(double v) {
        return Math.round(v * ROUND) / ROUND;
    }
}
