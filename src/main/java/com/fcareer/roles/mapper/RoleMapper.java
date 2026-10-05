package com.fcareer.roles.mapper;

import com.fcareer.fitengine.model.AcceleRate;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitRole;
import com.fcareer.fitengine.model.Weapon;
import com.fcareer.roles.entity.RoleEntity;
import com.fcareer.roles.model.response.RoleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class RoleMapper {

    public RoleResponse toResponse(RoleEntity role) {
        return RoleResponse.builder()
                .id(role.getId())
                .baseRole(role.getBaseRole())
                .name(role.getName())
                .positions(role.getPositions())
                .focus(role.getFocus())
                .attrWeights(role.getAttrWeights())
                .weapons(role.getWeapons())
                .playstyles(role.getPlaystyles())
                .accelerate(role.getAccelerate())
                .globalFloor(role.getGlobalFloor())
                .movementTags(role.getMovementTags())
                .build();
    }

    public FitRole toFitRole(RoleEntity role) {
        Map<Attr, Double> weights = new EnumMap<>(Attr.class);
        role.getAttrWeights().forEach((key, weight) -> weights.put(Attr.requireKey(key), weight));
        Map<Attr, Integer> floor = new EnumMap<>(Attr.class);
        role.getGlobalFloor().forEach((key, value) -> floor.put(Attr.requireKey(key), value));
        Set<AcceleRate> acceleRates = EnumSet.noneOf(AcceleRate.class);
        role.getAccelerate().forEach(label -> AcceleRate.fromLabel(label).ifPresent(acceleRates::add));
        return FitRole.builder()
                .id(role.getId())
                .baseRole(role.getBaseRole())
                .name(role.getName())
                .positions(role.getPositions())
                .focus(role.getFocus())
                .attrWeights(weights)
                .weapons(role.getWeapons().stream()
                        .map(w -> new Weapon(Attr.requireKey(w.getAttr()), w.getHardMin(), w.getTarget()))
                        .toList())
                .priorityPlaystyles(role.getPlaystyles().getPriority())
                .bonusPlaystyles(role.getPlaystyles().getBonus())
                .acceleRates(acceleRates)
                .globalFloor(floor)
                .movementTags(role.getMovementTags())
                .build();
    }
}
