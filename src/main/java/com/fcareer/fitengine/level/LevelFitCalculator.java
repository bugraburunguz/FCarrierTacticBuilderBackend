package com.fcareer.fitengine.level;

import java.util.Arrays;

/**
 * levelFit hesabı (13 §3.2 madde 11): aynı (mevki, rol, focus) için OVR bandındaki katalog oyuncularının currentFit dağılımı içinde yüzdelik.
 * Saf fonksiyonlar; DB ve motor bilgisi yok.
 */
public final class LevelFitCalculator {

    public static final int QUANTILE_POINTS = 101;
    private static final double MAX_PERCENT = 100.0;
    /** Dar bantlarda aşırı duyarlılığı önleyen yayılma tabanı (currentFit puanı). */
    public static final double SPREAD_FLOOR = 8.0;
    private static final int LOWER_QUARTILE_INDEX = 25;
    private static final int MEDIAN_INDEX = 50;
    private static final int UPPER_QUARTILE_INDEX = 75;

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

    /**
     * levelFit (E3-5c): 50 + 50·tanh((fit − bant medyanı) / max(bant IQR, SPREAD_FLOOR)).
     * Tam yüzdelik, dar ve küçük bantlarda (üst OVR) 3-5 puanlık farkı büyük sıçramaya çevirir; burada eğim en çok 50/SPREAD_FLOOR = 6,25 puan/puan
     * (3 puanlık fark ≤ 18,75), dağılım genişse IQR devreye girer. Sonuç (0, 100) açık aralığındadır; medyan = 50.
     */
    public static double smooth(double[] q, double fit) {
        double median = q[MEDIAN_INDEX];
        double iqr = q[UPPER_QUARTILE_INDEX] - q[LOWER_QUARTILE_INDEX];
        double z = (fit - median) / Math.max(iqr, SPREAD_FLOOR);
        return 50.0 + 50.0 * Math.tanh(z);
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
