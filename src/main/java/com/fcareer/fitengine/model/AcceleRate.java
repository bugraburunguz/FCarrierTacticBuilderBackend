package com.fcareer.fitengine.model;

import java.util.Optional;

public enum AcceleRate {
    EXPLOSIVE,
    CONTROLLED,
    LENGTHY;

    public static Optional<AcceleRate> fromLabel(String label) {
        if (label == null || label.isBlank()) {
            return Optional.empty();
        }
        String normalized = label.trim().toUpperCase();
        if (normalized.startsWith("CTRL")) {
            return Optional.of(CONTROLLED);
        }
        // "Controlled Explosive" / "Mostly Explosive" gibi FC alt-arketipleri ana aileye düşer.
        if (normalized.contains("EXPLOSIVE")) {
            return Optional.of(EXPLOSIVE);
        }
        if (normalized.contains("LENGTHY")) {
            return Optional.of(LENGTHY);
        }
        if (normalized.contains("CONTROLLED")) {
            return Optional.of(CONTROLLED);
        }
        return Optional.empty();
    }
}
