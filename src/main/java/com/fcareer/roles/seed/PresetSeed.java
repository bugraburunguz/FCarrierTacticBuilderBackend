package com.fcareer.roles.seed;

import java.util.List;
import java.util.Map;

public record PresetSeed(
        String id,
        String kind,
        String name,
        String club,
        String coach,
        String season,
        String formation,
        Map<String, Object> settings,
        String signatureTr,
        Map<String, String> roleHints,
        Map<String, List<String>> slotTags,
        String sourceNote) {
}
