package com.fcareer.roles.seed;

import com.fcareer.tactics.model.FormationDefinition;

import java.util.List;

public record ReferenceSeedBundle(
        RoleWeighting weighting,
        List<RoleSeed> roles,
        List<BehaviorTagSeed> tags,
        List<FormationDefinition> formations,
        List<PresetSeed> presets) {
}
