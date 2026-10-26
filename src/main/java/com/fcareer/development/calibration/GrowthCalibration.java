package com.fcareer.development.calibration;

import com.fcareer.development.service.GrowthProjector;
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

    /** teamId (GROWTH-CAL): görüntüdeki takım; altyapı/A takım ayrımı için. */
    public record SnapshotPlayer(long playerId, Integer age, Integer overall, Map<Attr, Integer> attrs, Integer teamId) {
        public SnapshotPlayer(long playerId, Integer age, Integer overall, Map<Attr, Integer> attrs) {
            this(playerId, age, overall, attrs, null);
        }
    }

    /**
     * GROWTH-CAL: altyapı (önceki görüntüde altyapı takımında) ya da A takım × yaş bandı için gerçek OVR artışı ve önerilen tavan (sezona ölçekli
     * 90. yüzdelik, yuvarlanmış). configCap config'teki tavan; config otomatik değişmez. perSeasonScale: 365 / aradaki gün (bilinmiyorsa 1).
     */
    public record StageBand(String stage, String label, int players, double meanDelta, int maxDelta, double perSeasonScale, int suggestedCap, int configCap) {
    }

    /** Gelişim yaşları (≤ 23) için yaş bantları. */
    private static final int[][] YOUTH_BANDS = {{0, 15}, {16, 17}, {18, 19}, {20, 21}, {22, 23}};
    private static final double P90 = 0.9;
    private static final double DAYS_PER_SEASON = 365.0;
    private static final long MIN_INTERVAL_DAYS = 30;
    public static final String ACADEMY = "ALTYAPI";
    public static final String FIRST_TEAM = "A_TAKIM";

    public static List<StageBand> stageBands(List<SnapshotPlayer> before, List<SnapshotPlayer> after, Integer academyTeamId, Long intervalDays,
                                             int academyCap, int firstTeamCap) {
        Map<Long, SnapshotPlayer> earlier = before.stream().collect(Collectors.toMap(SnapshotPlayer::playerId, Function.identity(), (a, b) -> a));
        double scale = Objects.nonNull(intervalDays) && intervalDays >= MIN_INTERVAL_DAYS ? DAYS_PER_SEASON / intervalDays : 1.0;
        List<StageBand> out = new ArrayList<>();
        for (String stage : List.of(ACADEMY, FIRST_TEAM)) {
            for (int[] band : YOUTH_BANDS) {
                List<Integer> deltas = after.stream().filter((p) -> earlier.containsKey(p.playerId()) && Objects.nonNull(p.overall())).map((p) -> {
                    SnapshotPlayer b = earlier.get(p.playerId());
                    boolean academy = Objects.nonNull(academyTeamId) && academyTeamId.equals(b.teamId());
                    boolean inStage = ACADEMY.equals(stage) == academy;
                    boolean inBand = Objects.nonNull(b.age()) && b.age() >= band[0] && b.age() <= band[1] && Objects.nonNull(b.overall());
                    return inStage && inBand ? p.overall() - b.overall() : null;
                }).filter(Objects::nonNull).sorted().toList();
                if (deltas.isEmpty()) {
                    continue;
                }
                int p90 = deltas.get((int) Math.ceil(P90 * deltas.size()) - 1);
                String label = band[0] == 0 ? "≤" + band[1] : band[0] + "-" + band[1];
                out.add(new StageBand(stage, label, deltas.size(), round(deltas.stream().mapToInt(Integer::intValue).average().orElse(0)),
                        deltas.get(deltas.size() - 1), round(scale), (int) Math.round(p90 * scale), ACADEMY.equals(stage) ? academyCap : firstTeamCap));
            }
        }
        return out;
    }

    public record AttributeGain(Attr attr, double meanDelta) {
    }

    public record AgeBand(String label, int players, double meanOverallDelta) {
    }

    /**
     * stageBands/intervalDays (GROWTH-CAL): altyapı/A takım × yaş için gerçek artış ve önerilen tavan; aradaki oyun günü (bilinmiyorsa null).
     * declineBands (GT-a): yaş grubuna göre gerçek düşüş.
     */
    public record Calibration(String status, String message, Long fromSnapshot, Long toSnapshot, int players, int growingPlayers, double meanOverallDelta,
                              List<AttributeGain> attributes, List<AgeBand> ageBands, Double hardToTrainRatio, double modelHardToTrain,
                              List<StageBand> stageBands, Long intervalDays, List<DeclineBand> declineBands) {
        public Calibration(String status, String message, Long fromSnapshot, Long toSnapshot, int players, int growingPlayers, double meanOverallDelta,
                           List<AttributeGain> attributes, List<AgeBand> ageBands, Double hardToTrainRatio, double modelHardToTrain) {
            this(status, message, fromSnapshot, toSnapshot, players, growingPlayers, meanOverallDelta, attributes, ageBands, hardToTrainRatio, modelHardToTrain,
                    List.of(), null, List.of());
        }
    }

    /**
     * GT-a madde 4: yaş grubunda (24–29, 30–31, 32–33, 34+) sezona ölçekli ortalama OVR değişimi ve attribute grubu (fiziksel, teknik, zihinsel) başına
     * ortalama attribute değişimi. Modelin düşüş başlangıç yaşlarıyla kıyas için; config otomatik değişmez.
     */
    public record DeclineBand(String label, int players, double meanOverallDelta, double physical, double technical, double mental) {
    }

    private static final int[][] DECLINE_BANDS = {{24, 29}, {30, 31}, {32, 33}, {34, 99}};

    public static List<DeclineBand> declineBands(List<SnapshotPlayer> before, List<SnapshotPlayer> after, Long intervalDays) {
        Map<Long, SnapshotPlayer> earlier = before.stream().collect(Collectors.toMap(SnapshotPlayer::playerId, Function.identity(), (a, b) -> a));
        double scale = Objects.nonNull(intervalDays) && intervalDays >= MIN_INTERVAL_DAYS ? DAYS_PER_SEASON / intervalDays : 1.0;
        List<DeclineBand> out = new ArrayList<>();
        for (int[] band : DECLINE_BANDS) {
            List<SnapshotPlayer[]> pairs = after.stream().filter((p) -> earlier.containsKey(p.playerId()) && Objects.nonNull(p.overall())).map((p) -> new SnapshotPlayer[]{earlier.get(p.playerId()), p})
                    .filter((x) -> Objects.nonNull(x[0].age()) && Objects.nonNull(x[0].overall()) && x[0].age() >= band[0] && x[0].age() <= band[1]).toList();
            if (pairs.isEmpty()) {
                continue;
            }
            String label = band[1] == 99 ? band[0] + "+" : band[0] + "-" + band[1];
            double ovr = pairs.stream().mapToInt((x) -> x[1].overall() - x[0].overall()).average().orElse(0);
            out.add(new DeclineBand(label, pairs.size(), round(ovr * scale), groupDelta(pairs, GrowthProjector.DeclineGroup.PHYSICAL, scale),
                    groupDelta(pairs, GrowthProjector.DeclineGroup.TECHNICAL, scale), groupDelta(pairs, GrowthProjector.DeclineGroup.MENTAL, scale)));
        }
        return out;
    }

    /** Gruptaki attribute'ların oyuncu başına ortalama değişimi (kaleci attribute'ları hariç). */
    private static double groupDelta(List<SnapshotPlayer[]> pairs, GrowthProjector.DeclineGroup group, double scale) {
        return round(pairs.stream().mapToDouble((x) -> {
            List<Attr> attrs = java.util.Arrays.stream(Attr.values()).filter((a) -> !GOALKEEPING.contains(a) && GrowthProjector.group(a) == group
                    && x[0].attrs().containsKey(a) && x[1].attrs().containsKey(a)).toList();
            return attrs.isEmpty() ? 0 : attrs.stream().mapToInt((a) -> x[1].attrs().get(a) - x[0].attrs().get(a)).average().orElse(0);
        }).average().orElse(0) * scale);
    }

    public static Calibration noData() {
        return new Calibration("NO_DATA", NO_DATA, null, null, 0, 0, 0, List.of(), List.of(), null, 0);
    }

    /** GROWTH-CAL: altyapı/A takım bantlarını ve aralığı rapora ekler. */
    public static Calibration withStageBands(Calibration c, List<StageBand> bands, Long intervalDays) {
        return new Calibration(c.status(), c.message(), c.fromSnapshot(), c.toSnapshot(), c.players(), c.growingPlayers(), c.meanOverallDelta(), c.attributes(),
                c.ageBands(), c.hardToTrainRatio(), c.modelHardToTrain(), bands, intervalDays, c.declineBands());
    }

    /** GT-a: yaş grubu düşüş bantlarını rapora ekler. */
    public static Calibration withDeclineBands(Calibration c, List<DeclineBand> bands) {
        return new Calibration(c.status(), c.message(), c.fromSnapshot(), c.toSnapshot(), c.players(), c.growingPlayers(), c.meanOverallDelta(), c.attributes(),
                c.ageBands(), c.hardToTrainRatio(), c.modelHardToTrain(), c.stageBands(), c.intervalDays(), bands);
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
                c.hardToTrainRatio(), c.modelHardToTrain(), c.stageBands(), c.intervalDays(), c.declineBands());
    }

    private static double round(double v) {
        return Math.round(v * ROUND) / ROUND;
    }
}
