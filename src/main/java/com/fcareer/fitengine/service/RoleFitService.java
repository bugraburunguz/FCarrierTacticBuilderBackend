package com.fcareer.fitengine.service;

import com.fcareer.fitengine.configuration.FitEngineProperties;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.FitRole;
import com.fcareer.fitengine.model.IntentProfile;
import com.fcareer.fitengine.model.Weapon;
import com.fcareer.fitengine.model.WeaponState;
import com.fcareer.fitengine.model.response.RoleFitBreakdown;
import com.fcareer.fitengine.model.response.RoleFitResponse;
import com.fcareer.fitengine.model.response.WeaponCheck;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class RoleFitService {

    private static final double MAX_ATTR = 99.0;
    private static final int MIN_RATING = 1;
    private static final int MAX_RATING = 99;
    private static final double MAX_SCORE = 100.0;

    private final FitEngineProperties properties;
    private final NlgService nlgService;

    public RoleFitResponse calculate(FitPlayer player, FitRole role) {
        return calculate(player, role, null, null);
    }

    public RoleFitResponse calculate(FitPlayer player, FitRole role, IntentProfile intent, String slotPosition) {
        List<String> reasons = new ArrayList<>();

        Map<Attr, Double> weights = effectiveWeights(role, intent);
        double base = weightedBase(player, weights);

        List<WeaponCheck> weaponChecks = new ArrayList<>();
        List<String> weakWeapons = new ArrayList<>();
        WeaponState worst = WeaponState.GREEN;
        double weaponPenalty = 0;
        for (Weapon weapon : role.getWeapons()) {
            WeaponCheck check = checkWeapon(player, weapon);
            weaponChecks.add(check);
            weaponPenalty += check.getPenalty();
            worst = worst.worse(check.getState());
            if (check.getState() != WeaponState.GREEN) {
                weakWeapons.add(nlgService.attrLabel(weapon.getAttr()));
            }
            reasons.add(nlgService.weaponReason(weapon, check.getValue(), check.getEffectiveMin(),
                    check.getState(), check.isPlaystylePlusCompensated()));
        }

        double playstyleBonus = playstyleBonus(player, role, reasons);
        double accelerate = accelerateScore(player, role, reasons);

        String position = resolvePosition(intent, slotPosition);
        boolean outOfPosition = isOutOfPosition(player, role, position);
        double outOfPositionPenalty = 0;
        if (outOfPosition) {
            outOfPositionPenalty = properties.getOutOfPositionPenalty();
            reasons.add(nlgService.outOfPosition(Objects.nonNull(position) ? position : String.join("/", role.getPositions())));
        }

        double floorPenalty = floorPenalty(player, role, reasons);
        boolean gateTripped = defensiveGateTripped(player, role, position, reasons);
        double gatePenalty = gateTripped ? properties.getDefensiveGate().getHeavyPenalty() : 0;
        if (gateTripped) {
            worst = WeaponState.RED;
            weakWeapons.add(nlgService.attrLabel(Attr.DEF_AWARENESS));
        }

        double raw = base * MAX_SCORE + playstyleBonus + accelerate - weaponPenalty - outOfPositionPenalty - floorPenalty - gatePenalty;
        double score = round1(clamp(raw, 0, MAX_SCORE));

        int projected = projectedRating(base, playstyleBonus, accelerate, worst);
        if (gateTripped) {
            projected = Math.min(projected, properties.getDefensiveGate().getProjectedCap());
        }

        return RoleFitResponse.builder()
                .playerId(player.getId())
                .playerName(player.getName())
                .roleId(role.getId())
                .roleName(role.getName())
                .score(score)
                .projectedRating(projected)
                .badge(worst)
                .badgeText(nlgService.badgeText(worst, weakWeapons))
                .outOfPosition(outOfPosition)
                .reasons(reasons)
                .weaponChecks(weaponChecks)
                .engineVersion("v1")
                .breakdown(RoleFitBreakdown.builder()
                        .base(round1(base * MAX_SCORE))
                        .playstyleBonus(round1(playstyleBonus))
                        .accelerate(round1(accelerate))
                        .weaponPenalty(round1(weaponPenalty))
                        .outOfPosition(round1(outOfPositionPenalty))
                        .floorPenalty(round1(floorPenalty))
                        .build())
                .build();
    }

    public static double norm(int value) {
        return clamp(value, 0, MAX_ATTR) / MAX_ATTR;
    }

    private String resolvePosition(IntentProfile intent, String slotPosition) {
        if (Objects.nonNull(slotPosition)) {
            return slotPosition;
        }
        if (Objects.nonNull(intent)) {
            return intent.getSlotPosition();
        }
        return null;
    }

    private Map<Attr, Double> effectiveWeights(FitRole role, IntentProfile intent) {
        if (Objects.isNull(intent) || Objects.isNull(intent.getWeaponBoost()) || intent.getWeaponBoost().isEmpty()) {
            return role.getAttrWeights();
        }
        Map<Attr, Double> merged = new EnumMap<>(Attr.class);
        merged.putAll(role.getAttrWeights());
        intent.getWeaponBoost().forEach((attr, boost) -> merged.merge(attr, boost, Double::sum));
        double total = merged.values().stream().mapToDouble(Double::doubleValue).sum();
        merged.replaceAll((attr, weight) -> weight / total);
        return merged;
    }

    private double weightedBase(FitPlayer player, Map<Attr, Double> weights) {
        double base = 0;
        for (Map.Entry<Attr, Double> entry : weights.entrySet()) {
            base += entry.getValue() * norm(player.attr(entry.getKey()));
        }
        return base;
    }

    private WeaponCheck checkWeapon(FitPlayer player, Weapon weapon) {
        boolean compensated = hasRelevantPlaystylePlus(player, weapon.getAttr());
        int effectiveMin = weapon.getHardMin() - (compensated ? properties.getWeapon().getPlaystylePlusCompensation() : 0);
        int value = player.attr(weapon.getAttr());

        WeaponState state;
        double penalty;
        if (value >= weapon.getTarget()) {
            state = WeaponState.GREEN;
            penalty = 0;
        } else if (value >= effectiveMin) {
            state = WeaponState.YELLOW;
            penalty = (weapon.getTarget() - value) * properties.getWeapon().getYellowPenaltyPerPoint();
        } else {
            state = WeaponState.RED;
            penalty = (weapon.getTarget() - effectiveMin) * properties.getWeapon().getYellowPenaltyPerPoint()
                    + (effectiveMin - value) * properties.getWeapon().getRedPenaltyPerPoint();
        }

        return WeaponCheck.builder()
                .attr(weapon.getAttr().getKey())
                .value(value)
                .hardMin(weapon.getHardMin())
                .effectiveMin(effectiveMin)
                .target(weapon.getTarget())
                .playstylePlusCompensated(compensated)
                .state(state)
                .penalty(round1(penalty))
                .build();
    }

    private boolean hasRelevantPlaystylePlus(FitPlayer player, Attr attr) {
        List<String> relevant = properties.getPlaystyleRelevance().getOrDefault(attr.getKey(), List.of());
        return relevant.stream().anyMatch(player::hasPlaystylePlus);
    }

    private double playstyleBonus(FitPlayer player, FitRole role, List<String> reasons) {
        FitEngineProperties.Playstyle config = properties.getPlaystyle();
        double bonus = 0;
        for (String playstyle : role.getPriorityPlaystyles()) {
            if (player.hasPlaystyle(playstyle)) {
                boolean plus = player.hasPlaystylePlus(playstyle);
                bonus += plus ? config.getPriorityPlus() : config.getPriority();
                reasons.add(nlgService.playstyleReason(playstyle, plus));
            }
        }
        for (String playstyle : role.getBonusPlaystyles()) {
            if (player.hasPlaystyle(playstyle)) {
                bonus += player.hasPlaystylePlus(playstyle) ? config.getBonusPlus() : config.getBonus();
            }
        }
        return bonus;
    }

    private double accelerateScore(FitPlayer player, FitRole role, List<String> reasons) {
        if (role.getAcceleRates().isEmpty() || Objects.isNull(player.getAcceleRate())) {
            return 0;
        }
        boolean match = role.getAcceleRates().contains(player.getAcceleRate());
        reasons.add(nlgService.accelerateReason(match, player.getAcceleRate()));
        return match ? properties.getAccelerate().getMatch() : properties.getAccelerate().getMismatch();
    }

    private boolean isOutOfPosition(FitPlayer player, FitRole role, String slotPosition) {
        List<String> accepted = Objects.nonNull(slotPosition) ? List.of(slotPosition) : role.getPositions();
        for (String position : accepted) {
            if (playsPosition(player, position)) {
                return false;
            }
        }
        return true;
    }

    private boolean playsPosition(FitPlayer player, String position) {
        if (player.getPositions().contains(position)) {
            return true;
        }
        List<String> equivalents = properties.getPositionEquivalents().getOrDefault(position, List.of());
        return equivalents.stream().anyMatch(player.getPositions()::contains);
    }

    private boolean defensiveGateTripped(FitPlayer player, FitRole role, String position, List<String> reasons) {
        FitEngineProperties.DefensiveGate gate = properties.getDefensiveGate();
        String group = defensiveGroup(Objects.nonNull(position) ? position : singlePosition(role));
        if (!gate.isEnabled() || Objects.isNull(group)) {
            return false;
        }
        int defAwareness = player.attr(Attr.DEF_AWARENESS);
        int tackling = (player.attr(Attr.STANDING_TACKLE) + player.attr(Attr.INTERCEPTIONS)) / 2;
        boolean tripped = defAwareness < gate.getDefFloor().get(group) || tackling < gate.getTackleFloor().get(group);
        if (tripped) {
            reasons.add(nlgService.defensiveGate(group, defAwareness, tackling));
        }
        return tripped;
    }

    private static String singlePosition(FitRole role) {
        return role.getPositions().stream().map(RoleFitService::defensiveGroup).distinct().count() == 1
                && Objects.nonNull(defensiveGroup(role.getPositions().get(0))) ? role.getPositions().get(0) : null;
    }

    private static String defensiveGroup(String position) {
        if (Objects.isNull(position)) {
            return null;
        }
        return switch (position) {
            case "CB" -> "CB";
            case "LB", "RB", "LWB", "RWB" -> "FB";
            case "CDM" -> "CDM";
            default -> null;
        };
    }

    private double floorPenalty(FitPlayer player, FitRole role, List<String> reasons) {
        double penalty = 0;
        for (Map.Entry<Attr, Integer> floor : role.getGlobalFloor().entrySet()) {
            int value = player.attr(floor.getKey());
            int gap = floor.getValue() - value;
            if (gap > 0) {
                penalty += gap * properties.getGlobalFloorPenaltyPerPoint();
                reasons.add(nlgService.floorReason(floor.getKey(), value, floor.getValue()));
            }
        }
        return penalty;
    }

    private int projectedRating(double base, double playstyleBonus, double accelerate, WeaponState worst) {
        FitEngineProperties.Projected config = properties.getProjected();
        int projected = (int) Math.round(base * MAX_ATTR + playstyleBonus * config.getPlaystyleFactor()
                + accelerate * config.getAccelerateFactor());
        if (worst == WeaponState.RED) {
            projected = Math.min(projected, config.getRedCap());
        } else if (worst == WeaponState.YELLOW) {
            projected = Math.min(projected, config.getYellowCap());
        }
        return (int) clamp(projected, MIN_RATING, MAX_RATING);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
