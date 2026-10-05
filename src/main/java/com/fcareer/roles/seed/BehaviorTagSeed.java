package com.fcareer.roles.seed;

import java.util.List;

public record BehaviorTagSeed(
        String id,
        String position,
        String labelTr,
        List<String> weaponAttrs,
        List<String> playstyleWeapons,
        int expectedMin,
        String roleVote,
        List<String> conflicts,
        String nlgTr,
        String accelerateHint) {
}
