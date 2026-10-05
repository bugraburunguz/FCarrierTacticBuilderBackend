package com.fcareer.tactics.service;

import com.fcareer.common.exception.InvalidTacticException;
import com.fcareer.common.exception.TagConflictException;
import com.fcareer.fitengine.configuration.FitEngineProperties;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitRole;
import com.fcareer.fitengine.model.FitTag;
import com.fcareer.fitengine.model.IntentProfile;
import com.fcareer.roles.service.BehaviorTagService;
import com.fcareer.roles.service.RoleService;
import com.fcareer.tactics.entity.PresetEntity;
import com.fcareer.tactics.model.FormationDefinition;
import com.fcareer.tactics.model.FormationSlot;
import com.fcareer.tactics.model.request.SlotSelection;
import com.fcareer.tactics.model.request.TacticRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TacticResolver {

    private final FormationRegistry formationRegistry;
    private final PresetService presetService;
    private final RoleService roleService;
    private final BehaviorTagService behaviorTagService;
    private final FitEngineProperties fitEngineProperties;

    public ResolvedTactic resolve(TacticRequest request) {
        Optional<PresetEntity> preset = Optional.ofNullable(request.getPresetId()).map(presetService::getEntity);
        String formationId = Optional.ofNullable(request.getFormation())
                .or(() -> preset.map(PresetEntity::getFormation))
                .orElseThrow(() -> new InvalidTacticException("formation"));
        FormationDefinition formation = formationRegistry.get(formationId);

        Map<String, SlotSelection> selections = selectionsBySlot(formation, preset, request.getSlots());
        Map<String, FitRole> roles = roleService.fitRolesById();
        Map<String, FitTag> tags = behaviorTagService.fitTagsById();

        List<IntentProfile> profiles = new ArrayList<>();
        for (FormationSlot slot : formation.slots()) {
            SlotSelection selection = selections.get(slot.slotId());
            profiles.add(toProfile(slot, selection, roles, tags));
        }
        log.info("Tactic resolved formation : {} preset : {}", formationId, request.getPresetId());
        return new ResolvedTactic(formationId, request.getPresetId(), profiles);
    }

    private Map<String, SlotSelection> selectionsBySlot(FormationDefinition formation, Optional<PresetEntity> preset,
                                                        List<SlotSelection> requested) {
        Map<String, SlotSelection> selections = new LinkedHashMap<>();
        preset.filter(p -> formation.id().equals(p.getFormation()))
                .map(PresetEntity::getSlotTags)
                .ifPresent(slotTags -> slotTags.forEach((slotId, tagIds) ->
                        selections.put(slotId, SlotSelection.builder().slotId(slotId).tags(tagIds).build())));
        if (Objects.isNull(requested)) {
            return selections;
        }
        for (SlotSelection selection : requested) {
            if (formation.slot(selection.getSlotId()).isEmpty()) {
                log.warn("Unknown slot in tactic slot : {} formation : {}", selection.getSlotId(), formation.id());
                throw new InvalidTacticException("slot " + selection.getSlotId());
            }
            selections.put(selection.getSlotId(), selection);
        }
        return selections;
    }

    private IntentProfile toProfile(FormationSlot slot, SlotSelection selection, Map<String, FitRole> roles,
                                    Map<String, FitTag> tagCatalog) {
        List<String> tagIds = Objects.isNull(selection) ? List.of() : selection.getTags();
        String roleId = Objects.isNull(selection) ? null : selection.getRoleId();
        return buildProfile(slot.slotId(), slot.position(), slot.group(), slot.defaultRole(), roleId, tagIds, roles, tagCatalog);
    }

    public IntentProfile slotIntent(String slotId, String position, String group, String roleId, List<String> tagIds) {
        return buildProfile(slotId, position, group, null, roleId, Objects.isNull(tagIds) ? List.of() : tagIds,
                roleService.fitRolesById(), behaviorTagService.fitTagsById());
    }

    private IntentProfile buildProfile(String slotId, String position, String group, String defaultRole, String roleId,
                                       List<String> tagIds, Map<String, FitRole> roles, Map<String, FitTag> tagCatalog) {
        List<FitTag> tags = selectedTags(slotId, group, tagIds, tagCatalog);
        FitRole role = resolveRole(slotId, roleId, defaultRole, tags, roles);
        return IntentProfile.builder()
                .slotId(slotId)
                .slotPosition(position)
                .slotGroup(group)
                .tags(tags)
                .weaponBoost(weaponBoost(tags))
                .resolvedRole(role)
                .build();
    }

    private List<FitTag> selectedTags(String slotId, String group, List<String> tagIds, Map<String, FitTag> tagCatalog) {
        List<FitTag> tags = new ArrayList<>();
        for (String tagId : tagIds) {
            FitTag tag = tagCatalog.get(tagId);
            if (Objects.isNull(tag) || (Objects.nonNull(group) && !tag.getPosition().equals(group))) {
                log.warn("Tag not allowed tag : {} slot : {}", tagId, slotId);
                throw new InvalidTacticException("tag " + tagId + " @ " + slotId);
            }
            for (FitTag chosen : tags) {
                if (chosen.getConflicts().contains(tagId)) {
                    log.warn("Conflicting tags slot : {} tags : {} / {}", slotId, chosen.getId(), tagId);
                    throw new TagConflictException(chosen.getLabelTr(), tag.getLabelTr());
                }
            }
            tags.add(tag);
        }
        return tags;
    }

    private FitRole resolveRole(String slotId, String requestedRoleId, String defaultRole, List<FitTag> tags,
                                Map<String, FitRole> roles) {
        String roleId = Optional.ofNullable(requestedRoleId)
                .or(() -> voteRole(tags))
                .orElse(defaultRole);
        FitRole role = Objects.isNull(roleId) ? null : roles.get(roleId);
        if (Objects.isNull(role)) {
            log.warn("Unknown role for slot : {} role : {}", slotId, roleId);
            throw new InvalidTacticException("role " + roleId);
        }
        return role;
    }

    private Optional<String> voteRole(List<FitTag> tags) {
        Map<String, Integer> votes = new LinkedHashMap<>();
        tags.stream()
                .map(FitTag::getRoleVote)
                .filter(Objects::nonNull)
                .forEach(vote -> votes.merge(vote, 1, Integer::sum));
        return votes.entrySet().stream()
                .reduce((best, next) -> next.getValue() > best.getValue() ? next : best)
                .map(Map.Entry::getKey);
    }

    private Map<Attr, Double> weaponBoost(List<FitTag> tags) {
        Map<Attr, Double> boost = new EnumMap<>(Attr.class);
        for (FitTag tag : tags) {
            for (Attr attr : tag.getWeaponAttrs()) {
                boost.merge(attr, fitEngineProperties.getIntentWeaponBoost(), Double::sum);
            }
        }
        return boost;
    }

    public record ResolvedTactic(String formation, String presetId, List<IntentProfile> slots) {
    }
}
