package com.fcareer.roles.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RoleWeighting(
        double weaponBend,
        Map<String, Map<String, Double>> positionBase,
        Map<String, Map<String, Double>> focusBend,
        Map<String, Integer> defaultGlobalFloor,
        Map<String, Integer> goalkeeperGlobalFloor) {
}
