package com.fcareer.tacticanalysis;

/** Hat yüksekliği bandı (13 §3.4): DEEP ≤30 / BALANCED ≤60 / HIGH ≤90 / AGGRESSIVE. */
public enum AnalysisBand {
    DEEP, BALANCED, HIGH, AGGRESSIVE;

    private static final int DEEP_MAX = 30;
    private static final int BALANCED_MAX = 60;
    private static final int HIGH_MAX = 90;

    public static AnalysisBand of(int depth) {
        if (depth <= DEEP_MAX) {
            return DEEP;
        }
        if (depth <= BALANCED_MAX) {
            return BALANCED;
        }
        return depth <= HIGH_MAX ? HIGH : AGGRESSIVE;
    }
}
