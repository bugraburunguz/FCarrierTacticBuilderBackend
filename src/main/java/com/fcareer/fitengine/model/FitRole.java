package com.fcareer.fitengine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Getter
@Builder
@AllArgsConstructor
public class FitRole {
    private final String id;
    private final String baseRole;
    private final String name;
    private final List<String> positions;
    private final String focus;
    private final Map<Attr, Double> attrWeights;
    private final List<Weapon> weapons;
    private final List<String> priorityPlaystyles;
    private final List<String> bonusPlaystyles;
    private final Set<AcceleRate> acceleRates;
    private final Map<Attr, Integer> globalFloor;
    private final List<String> movementTags;
}
