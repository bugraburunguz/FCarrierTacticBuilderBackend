package com.fcareer.tacticanalysis;

import java.util.Locale;

/** Build-up tipi; site adları "Short" / "Balanced" / "Counter" (EA: Short Passing / Balanced / Counter). */
public enum BuildUpType {
    SHORT, BALANCED, COUNTER;

    public static BuildUpType parse(String value) {
        if (value == null || value.isBlank()) {
            return BALANCED;
        }
        String v = value.trim().toUpperCase(Locale.ROOT);
        if (v.startsWith("SHORT")) {
            return SHORT;
        }
        return v.startsWith("COUNTER") ? COUNTER : BALANCED;
    }
}
