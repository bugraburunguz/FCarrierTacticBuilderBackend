package com.fcareer.recommendation.pipeline;

/** Edinme fizibilitesi (gizleme yok; her karar sayısal gerekçeli). */
public enum FeasibilityBucket {
    ALINABILIR("Alınabilir"),
    ZOR("Zor"),
    HAYAL("Hayal");

    private final String label;

    FeasibilityBucket(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
