package com.fcareer.mlclient.service;

import java.util.Objects;

/**
 * Piyasa değeri modeli yokken (eğitim verisinde değer sütunu bulunmuyor) kullanılan, kamuya açık genel şekle dayalı tahmin:
 * overall'a göre üstel eğri, genç oyuncuda potansiyele doğru kayma, 29 yaş sonrası düşüş, lig seviyesine göre ölçek.
 * Gerçek EA değeri değildir; her zaman MODELED olarak işaretlenir.
 */
public final class ModeledValueFormula {

    private static final int[] ANCHOR_OVERALL = {45, 55, 60, 65, 70, 75, 80, 85, 90, 95};
    private static final double[] ANCHOR_VALUE = {15_000, 100_000, 350_000, 1_000_000, 2_800_000, 8_000_000, 25_000_000, 70_000_000, 140_000_000, 200_000_000};
    private static final double[] TIER_FACTOR = {1.0, 1.0, 0.8, 0.6, 0.45};
    private static final double UNKNOWN_TIER_FACTOR = 0.7;
    private static final int PRIME_AGE_END = 29;
    private static final double DECAY_PER_YEAR = 0.1;
    private static final double MIN_AGE_FACTOR = 0.15;
    private static final long ROUND_SMALL = 5_000;
    private static final long ROUND_LARGE = 50_000;
    private static final long LARGE_FROM = 1_000_000;

    private ModeledValueFormula() {
    }

    public static long estimate(int overall, Integer potential, Integer age, Integer leagueTier) {
        double effective = overall + youthLift(overall, potential, age);
        double value = curve(effective) * ageFactor(age) * tierFactor(leagueTier);
        return round(Math.max(ANCHOR_VALUE[0], value));
    }

    static double youthLift(int overall, Integer potential, Integer age) {
        if (Objects.isNull(potential) || Objects.isNull(age) || potential <= overall) {
            return 0;
        }
        double weight = age <= 19 ? 0.6 : age <= 21 ? 0.5 : age <= 23 ? 0.35 : age <= 25 ? 0.2 : 0;
        return (potential - overall) * weight;
    }

    static double curve(double rating) {
        if (rating <= ANCHOR_OVERALL[0]) {
            return ANCHOR_VALUE[0];
        }
        for (int i = 1; i < ANCHOR_OVERALL.length; i++) {
            if (rating <= ANCHOR_OVERALL[i]) {
                double share = (rating - ANCHOR_OVERALL[i - 1]) / (ANCHOR_OVERALL[i] - ANCHOR_OVERALL[i - 1]);
                return Math.exp(Math.log(ANCHOR_VALUE[i - 1]) + share * (Math.log(ANCHOR_VALUE[i]) - Math.log(ANCHOR_VALUE[i - 1])));
            }
        }
        return ANCHOR_VALUE[ANCHOR_VALUE.length - 1];
    }

    static double ageFactor(Integer age) {
        if (Objects.isNull(age) || age <= PRIME_AGE_END) {
            return 1.0;
        }
        return Math.max(MIN_AGE_FACTOR, 1.0 - DECAY_PER_YEAR * (age - PRIME_AGE_END));
    }

    static double tierFactor(Integer tier) {
        if (Objects.isNull(tier) || tier < 1) {
            return UNKNOWN_TIER_FACTOR;
        }
        return TIER_FACTOR[Math.min(tier, TIER_FACTOR.length) - 1];
    }

    private static long round(double value) {
        long step = value >= LARGE_FROM ? ROUND_LARGE : ROUND_SMALL;
        return Math.round(value / step) * step;
    }
}
