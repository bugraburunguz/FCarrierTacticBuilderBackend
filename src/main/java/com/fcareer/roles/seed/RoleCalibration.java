package com.fcareer.roles.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RoleCalibration(
        String version,
        double weaponBendWithoutEmphasis,
        double focusBendScale,
        Map<String, Map<String, Double>> positionBaseline,
        Map<String, Map<String, Double>> roleEmphasis) {
}
