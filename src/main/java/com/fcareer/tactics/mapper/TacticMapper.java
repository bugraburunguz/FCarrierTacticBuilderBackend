package com.fcareer.tactics.mapper;

import com.fcareer.fitengine.model.FitTag;
import com.fcareer.fitengine.model.IntentProfile;
import com.fcareer.tactics.entity.PresetEntity;
import com.fcareer.tactics.model.response.PresetResponse;
import com.fcareer.tactics.model.response.ResolvedSlotResponse;
import com.fcareer.tactics.model.response.ResolvedTacticResponse;
import com.fcareer.tactics.service.TacticResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TacticMapper {

    public PresetResponse toPresetResponse(PresetEntity preset) {
        return PresetResponse.builder()
                .id(preset.getId())
                .kind(preset.getKind())
                .name(preset.getName())
                .club(preset.getClub())
                .coach(preset.getCoach())
                .season(preset.getSeason())
                .formation(preset.getFormation())
                .settings(preset.getSettings())
                .signature(preset.getSignatureTr())
                .roleHints(preset.getRoleHints())
                .slotTags(preset.getSlotTags())
                .sourceNote(preset.getSourceNote())
                .build();
    }


    public ResolvedTacticResponse toResolvedResponse(TacticResolver.ResolvedTactic tactic) {
        return ResolvedTacticResponse.builder()
                .formation(tactic.formation())
                .presetId(tactic.presetId())
                .slots(tactic.slots().stream().map(this::toResolvedSlot).toList())
                .build();
    }

    private ResolvedSlotResponse toResolvedSlot(IntentProfile slot) {
        List<String> tagIds = slot.getTags().stream().map(FitTag::getId).toList();
        List<String> locked = slot.getTags().stream()
                .flatMap(t -> t.getConflicts().stream())
                .distinct()
                .sorted()
                .toList();
        return ResolvedSlotResponse.builder()
                .slotId(slot.getSlotId())
                .position(slot.getSlotPosition())
                .group(slot.getSlotGroup())
                .roleId(slot.getResolvedRole().getId())
                .roleName(slot.getResolvedRole().getName())
                .tags(tagIds)
                .lockedTags(locked)
                .build();
    }
}
