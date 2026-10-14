package com.fcareer.tacticanalysis;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.EnumMap;
import java.util.Map;

/** Metrik hesabı parametreleri (`tactic-rules.fc27.json` → params). Hepsi taslak, verified:false; koda gömülmez. */
public record TacticParams(double restDefenceDMin, double restDefenceDyMax, double offFootFactor, Map<BuildUpType, Double> buildUpRequired, double staminaSupplyFrom, double staminaSupplySpan,
                           double heightMin, double heightMax, double midfieldXMin, double midfieldXMax, double midfieldCutInDxMin, double wingLeftMax, double wingRightMin,
                           double crossTargetRMin, double crossCrosserMin) {

    public static TacticParams from(JsonNode p) {
        Map<BuildUpType, Double> required = new EnumMap<>(BuildUpType.class);
        for (BuildUpType t : BuildUpType.values()) {
            required.put(t, p.path("buildUpRequired").path(t.name()).asDouble());
        }
        return new TacticParams(p.path("restDefenceDMin").asDouble(), p.path("restDefenceDyMax").asDouble(), p.path("offFootFactor").asDouble(), required,
                p.path("staminaSupplyFrom").asDouble(), p.path("staminaSupplySpan").asDouble(), p.path("heightMin").asDouble(), p.path("heightMax").asDouble(),
                p.path("midfieldXMin").asDouble(), p.path("midfieldXMax").asDouble(), p.path("midfieldCutInDxMin").asDouble(),
                p.path("wingLeftMax").asDouble(), p.path("wingRightMin").asDouble(), p.path("crossTargetRMin").asDouble(), p.path("crossCrosserMin").asDouble());
    }
}
