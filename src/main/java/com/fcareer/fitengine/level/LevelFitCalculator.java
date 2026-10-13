package com.fcareer.fitengine.level;

import java.util.Arrays;

/**
 * levelFit hesabı (13 §3.2 madde 11): aynı (mevki, rol, focus) için OVR bandındaki katalog oyuncularının currentFit dağılımı içinde yüzdelik.
 * Saf fonksiyonlar; DB ve motor bilgisi yok.
 */
public final class LevelFitCalculator {

    public static final int QUANTILE_POINTS = 101;
    private static final double MAX_PERCENT = 100.0;

    /** OVR bandı: [lo, hi] dahil, count bandın içindeki oyuncu sayısı. */
    public record Band(int lo, int hi, int count) {
    }

    private LevelFitCalculator() {
    }

    /** ±halfWidth bandı; minPlayers altındaysa her adımda iki yana 1 genişler (maxHalfWidth'e kadar). sortedOvrs artan sıralı olmalı. */
    public static Band band(int[] sortedOvrs, int ovr, int minPlayers, int halfWidth, int maxHalfWidth) {
        int width = halfWidth;
        int count = countIn(sortedOvrs, ovr - width, ovr + width);
        while (count < minPlayers && width < maxHalfWidth) {
            width++;
            count = countIn(sortedOvrs, ovr - width, ovr + width);
        }
        return new Band(ovr - width, ovr + width, count);
    }

    /** 0..100 yüzdelik noktaları (101 değer), doğrusal enterpolasyon. */
    public static double[] quantiles(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        double[] q = new double[QUANTILE_POINTS];
        int last = sorted.length - 1;
        for (int p = 0; p < QUANTILE_POINTS; p++) {
            double position = p / MAX_PERCENT * last;
            int below = (int) Math.floor(position);
            int above = Math.min(below + 1, last);
            q[p] = sorted[below] + (position - below) * (sorted[above] - sorted[below]);
        }
        return q;
    }

    /** fit değerinin dağılımdaki yüzdeliği (0..100); eşit değerlerde orta nokta. */
    public static double percentile(double[] q, double fit) {
        int lessThan = 0;
        int lessOrEqual = 0;
        for (double v : q) {
            if (v < fit) {
                lessThan++;
            }
            if (v <= fit) {
                lessOrEqual++;
            }
        }
        if (lessOrEqual == 0) {
            return 0;
        }
        if (lessThan == q.length) {
            return MAX_PERCENT;
        }
        if (lessOrEqual > lessThan) {
            return (lessThan + lessOrEqual - 1) / 2.0;
        }
        double lower = q[lessThan - 1];
        return (lessThan - 1) + (fit - lower) / (q[lessThan] - lower);
    }

    private static int countIn(int[] sortedOvrs, int lo, int hi) {
        int count = 0;
        for (int v : sortedOvrs) {
            if (v >= lo && v <= hi) {
                count++;
            }
        }
        return count;
    }
}
