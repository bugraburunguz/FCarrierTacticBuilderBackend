package com.fcareer.roles.seed;

import java.util.List;
import java.util.Map;

public record RoleSeed(
        String id,
        String name,
        List<String> positions,
        String weightGroup,
        List<String> focuses,
        List<WeaponSeed> weapons,
        Map<String, List<WeaponSeed>> focusWeapons,
        Map<String, List<String>> focusExcludePositions,
        List<String> priorityPlaystyles,
        List<String> bonusPlaystyles,
        List<String> accelerate,
        Map<String, Integer> globalFloor,
        List<String> movementTags) {
}
