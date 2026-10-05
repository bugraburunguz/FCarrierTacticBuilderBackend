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
        List<RoleEntity> roles = new ArrayList<>();
        for (RoleSeed seed : seeds) {
            for (String focus : seed.focuses()) {
                roles.add(toRole(seed, focus, weighting));
            }
        }
        return roles;
    }

    public static String roleId(String baseRole, String focus) {
        return baseRole + "_" + focus.toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private RoleEntity toRole(RoleSeed seed, String focus, RoleWeighting weighting) {
        List<WeaponSeed> weapons = weaponsFor(seed, focus);
        RoleEntity role = new RoleEntity();
        role.setId(roleId(seed.id(), focus));
        role.setBaseRole(seed.id());
        role.setName(seed.name() + " (" + focus + ")");
        role.setPositions(seed.positions());
        role.setFocus(focus);
        role.setAttrWeights(weights(seed, focus, weapons, weighting));
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
