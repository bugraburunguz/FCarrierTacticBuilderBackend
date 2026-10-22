package com.fcareer.tactics;

import com.fcareer.fitengine.model.TeamSetup;

/**
 * Varsayılan takım kurulumu (KARIYER-UX FAZ 1). Kaynak: seed/tactic-defaults.json; web DEFAULT_SETUP (src/lib/tacticDefaults.json) ile birebir aynı
 * (TacticDefaultsParityTest). TeamSetup değiştirilebilir olduğundan sabit değil, her çağrıda yeni kopya verilir.
 */
public final class TacticDefaults {

    public static final String BUILD_UP = "Balanced";
    public static final int DEPTH = 50;

    private TacticDefaults() {
    }

    public static TeamSetup setup() {
        return TeamSetup.builder().buildUp(BUILD_UP).depth(DEPTH).build();
    }
}
