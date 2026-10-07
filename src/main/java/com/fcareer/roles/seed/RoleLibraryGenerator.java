package com.fcareer.roles.seed;

import com.fcareer.roles.entity.RoleEntity;
import com.fcareer.roles.model.RolePlaystyles;
import com.fcareer.roles.model.RoleWeapon;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Component
public class RoleLibraryGenerator {

    private static final String GOALKEEPER_GROUP = "GK";
    private static final double WEIGHT_PRECISION = 10000.0;

    public List<RoleEntity> generate(List<RoleSeed> seeds, RoleWeighting weighting) {
        return generate(seeds, weighting, null);
    }

    public List<RoleEntity> generate(List<RoleSeed> seeds, RoleWeighting weighting, RoleCalibration calibration) {
        List<RoleEntity> roles = new ArrayList<>();
        for (RoleSeed seed : seeds) {
            for (String focus : seed.focuses()) {
                roles.add(toRole(seed, focus, weighting, calibration));
            }
        }
        return roles;
    }

    public static String roleId(String baseRole, String focus) {
        return baseRole + "_" + focus.toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private RoleEntity toRole(RoleSeed seed, String focus, RoleWeighting weighting, RoleCalibration calibration) {
        List<WeaponSeed> weapons = weaponsFor(seed, focus);
        RoleEntity role = new RoleEntity();
        role.setId(roleId(seed.id(), focus));
        role.setBaseRole(seed.id());
        role.setName(seed.name() + " (" + focus + ")");
        role.setPositions(positionsFor(seed, focus));
        role.setFocus(focus);
        role.setAttrWeights(Objects.isNull(calibration) ? weights(seed, focus, weapons, weighting) : calibratedWeights(seed, focus, weapons, weighting, calibration));
        role.setWeapons(weapons.stream()
                .map(w -> RoleWeapon.builder().attr(w.attr()).hardMin(w.hardMin()).target(w.target()).build())
                .toList());
        role.setPlaystyles(RolePlaystyles.builder()
                .priority(orEmpty(seed.priorityPlaystyles()))
                .bonus(orEmpty(seed.bonusPlaystyles()))
                .build());
        role.setAccelerate(orEmpty(seed.accelerate()));
        role.setGlobalFloor(globalFloor(seed, weighting));
        role.setMovementTags(orEmpty(seed.movementTags()));
        return role;
    }

    private List<String> positionsFor(RoleSeed seed, String focus) {
        List<String> excluded = Objects.isNull(seed.focusExcludePositions()) ? List.of() : seed.focusExcludePositions().getOrDefault(focus, List.of());
        return seed.positions().stream().filter(p -> !excluded.contains(p)).toList();
    }

    private List<WeaponSeed> weaponsFor(RoleSeed seed, String focus) {
        if (Objects.nonNull(seed.focusWeapons()) && seed.focusWeapons().containsKey(focus)) {
            return seed.focusWeapons().get(focus);
        }
        return seed.weapons();
    }

    private Map<String, Double> weights(RoleSeed seed, String focus, List<WeaponSeed> weapons, RoleWeighting weighting) {
        Map<String, Double> base = weighting.positionBase().get(seed.weightGroup());
        if (Objects.isNull(base)) {
            throw new IllegalStateException("Unknown weight group: " + seed.weightGroup() + " role: " + seed.id());
        }
        Map<String, Double> weights = new LinkedHashMap<>(base);
        for (WeaponSeed weapon : weapons) {
            weights.merge(weapon.attr(), weighting.weaponBend(), Double::sum);
        }
        Map<String, Double> focusBend = weighting.focusBend().getOrDefault(focus, Map.of());
        focusBend.forEach((attr, delta) -> weights.merge(attr, delta, Double::sum));
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        weights.replaceAll((attr, weight) -> Math.round(weight / total * WEIGHT_PRECISION) / WEIGHT_PRECISION);
        return weights;
    }

    private Map<String, Double> calibratedWeights(RoleSeed seed, String focus, List<WeaponSeed> weapons, RoleWeighting weighting,
                                                  RoleCalibration calibration) {
        Map<String, Double> baseline = calibration.positionBaseline().get(seed.weightGroup());
        if (Objects.isNull(baseline)) {
            throw new IllegalStateException("Unknown calibration group: " + seed.weightGroup() + " role: " + seed.id());
        }
        Map<String, Double> weights = new LinkedHashMap<>(baseline);
        Map<String, Double> emphasis = calibration.roleEmphasis().get(seed.id());
        Map<String, Double> focusEmphasis = calibration.roleEmphasis().get(seed.id() + ":" + focus);
        if (Objects.nonNull(emphasis)) {
            emphasis.forEach((attr, delta) -> weights.merge(attr, delta, Double::sum));
        }
        if (Objects.nonNull(focusEmphasis)) {
            focusEmphasis.forEach((attr, delta) -> weights.merge(attr, delta, Double::sum));
        }
        if (Objects.isNull(emphasis) && Objects.isNull(focusEmphasis)) {
            weapons.forEach(w -> weights.merge(w.attr(), calibration.weaponBendWithoutEmphasis(), Double::sum));
        }
        weighting.focusBend().getOrDefault(focus, Map.of())
                .forEach((attr, delta) -> weights.merge(attr, delta * calibration.focusBendScale(), Double::sum));
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        weights.replaceAll((attr, weight) -> Math.round(weight / total * WEIGHT_PRECISION) / WEIGHT_PRECISION);
        return weights;
    }

    private Map<String, Integer> globalFloor(RoleSeed seed, RoleWeighting weighting) {
        if (Objects.nonNull(seed.globalFloor())) {
            return seed.globalFloor();
        }
        if (GOALKEEPER_GROUP.equals(seed.weightGroup())) {
            return weighting.goalkeeperGlobalFloor();
        }
        return weighting.defaultGlobalFloor();
    }

    private static List<String> orEmpty(List<String> values) {
        return Objects.isNull(values) ? List.of() : values;
    }
}
