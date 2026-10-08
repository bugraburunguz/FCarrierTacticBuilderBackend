package com.fcareer.recommendation.service;

import com.fcareer.recommendation.model.response.Acquisition;
import com.fcareer.recommendation.model.response.Feasibility;

import java.util.Objects;

public final class FeasibilityClassifier {

    private static final int FREE_GAP_LIMIT = 10;
    private static final int REALISTIC_MAX_POINTS = 1;
    private static final int AMBITIOUS_MAX_POINTS = 3;

    private FeasibilityClassifier() {
    }

    /**
     * Tek eşik değil bileşik skor: lig/seviye dışı + değer/bütçe oranı + oyuncunun kadro ortalamasına farkı.
     * Eşikler ilk kalibrasyondur; before/after vaka seti ile doğrulanana kadar sabit kabul edilmemeli.
     */
    public static Feasibility classify(Acquisition acquisition, boolean inBand, Double valueRatio, int overallGap) {
        if (acquisition == Acquisition.FREE) {
            return overallGap > FREE_GAP_LIMIT ? Feasibility.AMBITIOUS : Feasibility.REALISTIC;
        }
        int points = (inBand ? 0 : 1) + ratioPoints(valueRatio) + gapPoints(overallGap);
        if (points <= REALISTIC_MAX_POINTS) {
            return Feasibility.REALISTIC;
        }
        return points <= AMBITIOUS_MAX_POINTS ? Feasibility.AMBITIOUS : Feasibility.UNREALISTIC;
    }

    static int ratioPoints(Double ratio) {
        if (Objects.isNull(ratio)) {
            return 1;
        }
        if (ratio <= 1.0) {
            return 0;
        }
        if (ratio <= 3.0) {
            return 1;
        }
        return ratio <= 8.0 ? 2 : 3;
    }

    static int gapPoints(int gap) {
        if (gap <= 4) {
            return 0;
        }
        if (gap <= 8) {
            return 1;
        }
        return gap <= 12 ? 2 : 3;
    }
}
