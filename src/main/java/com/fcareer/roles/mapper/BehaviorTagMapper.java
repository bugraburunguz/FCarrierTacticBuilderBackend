package com.fcareer.roles.mapper;

import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitTag;
import com.fcareer.roles.entity.BehaviorTagEntity;
import com.fcareer.roles.model.response.BehaviorTagResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class BehaviorTagMapper {

    public BehaviorTagResponse toResponse(BehaviorTagEntity tag) {
        return BehaviorTagResponse.builder()
                .id(tag.getId())
                .position(tag.getPosition())
                .label(tag.getLabelTr())
                .weaponAttrs(tag.getWeaponAttrs())
                .playstyleWeapons(tag.getPlaystyleWeapons())
                .expectedMin(tag.getExpectedMin())
                .roleVote(tag.getRoleVote())
                .modifier(Objects.isNull(tag.getRoleVote()))
                .conflicts(tag.getConflicts())
                .nlg(tag.getNlgTr())
                .accelerateHint(tag.getAccelerateHint())
                .build();
    }

    public FitTag toFitTag(BehaviorTagEntity tag) {
        return FitTag.builder()
                .id(tag.getId())
                .position(tag.getPosition())
                .labelTr(tag.getLabelTr())
                .weaponAttrs(tag.getWeaponAttrs().stream().map(Attr::requireKey).toList())
                .playstyleWeapons(tag.getPlaystyleWeapons())
                .expectedMin(tag.getExpectedMin())
                .roleVote(tag.getRoleVote())
                .conflicts(tag.getConflicts())
                .nlgTr(tag.getNlgTr())
                .build();
    }
}
