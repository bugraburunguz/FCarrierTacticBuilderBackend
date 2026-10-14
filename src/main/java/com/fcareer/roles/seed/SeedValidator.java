package com.fcareer.roles.seed;

import com.fcareer.fitengine.model.Attr;
import com.fcareer.roles.entity.RoleEntity;
import com.fcareer.tactics.model.FormationDefinition;
import com.fcareer.tactics.model.FormationSlot;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class SeedValidator {

    public static final Set<String> SLOT_GROUPS = Set.of("GK", "FB", "CB", "CDM", "CM", "CAM", "WM", "W", "ST");
    private static final int STARTING_ELEVEN = 11;

    public List<String> validate(ReferenceSeedBundle bundle, List<RoleEntity> roles, Map<String, List<String>> conflicts) {
        List<String> errors = new ArrayList<>();
        Set<String> roleIds = roles.stream().map(RoleEntity::getId).collect(Collectors.toSet());
        Map<String, BehaviorTagSeed> tags = bundle.tags().stream()
                .collect(Collectors.toMap(BehaviorTagSeed::id, Function.identity()));

        validateRoles(roles, errors);
        validateTags(bundle.tags(), roleIds, tags, errors);
        Map<String, FormationDefinition> formations = validateFormations(bundle.formations(), roleIds, errors);
        validatePresets(bundle.presets(), formations, tags, conflicts, errors);
        return errors;
    }

    public Map<String, List<String>> symmetricConflicts(List<BehaviorTagSeed> tags) {
        Map<String, Set<String>> conflicts = tags.stream()
                .collect(Collectors.toMap(BehaviorTagSeed::id, t -> new HashSet<>(orEmpty(t.conflicts()))));
        for (BehaviorTagSeed tag : tags) {
            for (String other : orEmpty(tag.conflicts())) {
                conflicts.computeIfAbsent(other, k -> new HashSet<>()).add(tag.id());
            }
        }
        return conflicts.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().stream().sorted().toList()));
    }

    private void validateRoles(List<RoleEntity> roles, List<String> errors) {
        for (RoleEntity role : roles) {
            role.getAttrWeights().keySet().forEach(a -> checkAttr(a, "role " + role.getId(), errors));
            role.getWeapons().forEach(w -> checkAttr(w.getAttr(), "role weapon " + role.getId(), errors));
            role.getGlobalFloor().keySet().forEach(a -> checkAttr(a, "role floor " + role.getId(), errors));
            double sum = role.getAttrWeights().values().stream().mapToDouble(Double::doubleValue).sum();
            if (Math.abs(sum - 1.0) > 0.01) {
                errors.add("role " + role.getId() + " weights sum " + sum);
            }
        }
    }

    private void validateTags(List<BehaviorTagSeed> tagList, Set<String> roleIds, Map<String, BehaviorTagSeed> tags,
                              List<String> errors) {
        for (BehaviorTagSeed tag : tagList) {
            if (!SLOT_GROUPS.contains(tag.position())) {
                errors.add("tag " + tag.id() + " unknown group " + tag.position());
            }
            if (orEmpty(tag.weaponAttrs()).isEmpty()) {
                errors.add("tag " + tag.id() + " has no weapon attr");
            }
            orEmpty(tag.weaponAttrs()).forEach(a -> checkAttr(a, "tag " + tag.id(), errors));
            if (Objects.nonNull(tag.roleVote()) && !roleIds.contains(tag.roleVote())) {
                errors.add("tag " + tag.id() + " votes unknown role " + tag.roleVote());
            }
            for (String conflict : orEmpty(tag.conflicts())) {
                BehaviorTagSeed other = tags.get(conflict);
                if (Objects.isNull(other)) {
                    errors.add("tag " + tag.id() + " conflicts with unknown tag " + conflict);
                } else if (!other.position().equals(tag.position())) {
                    errors.add("tag " + tag.id() + " conflicts across groups with " + conflict);
                }
            }
        }
    }

    private Map<String, FormationDefinition> validateFormations(List<FormationDefinition> formations, Set<String> roleIds,
                                                                List<String> errors) {
        for (FormationDefinition formation : formations) {
            if (formation.slots().size() != STARTING_ELEVEN) {
                errors.add("formation " + formation.id() + " has " + formation.slots().size() + " slots");
            }
            Set<String> slotIds = new HashSet<>();
            for (FormationSlot slot : formation.slots()) {
                if (!slotIds.add(slot.slotId())) {
                    errors.add("formation " + formation.id() + " duplicate slot " + slot.slotId());
                }
                if (!SLOT_GROUPS.contains(slot.group())) {
                    errors.add("formation " + formation.id() + " slot " + slot.slotId() + " unknown group");
                }
                if (!roleIds.contains(slot.defaultRole())) {
                    errors.add("formation " + formation.id() + " slot " + slot.slotId() + " unknown role " + slot.defaultRole());
                }
            }
        }
        return formations.stream().collect(Collectors.toMap(FormationDefinition::id, Function.identity()));
    }

    private void validatePresets(List<PresetSeed> presets, Map<String, FormationDefinition> formations,
                                 Map<String, BehaviorTagSeed> tags, Map<String, List<String>> conflicts, List<String> errors) {
        for (PresetSeed preset : presets) {
            FormationDefinition formation = formations.get(preset.formation());
            if (Objects.isNull(formation)) {
                errors.add("preset " + preset.id() + " unknown formation " + preset.formation());
                continue;
            }
            preset.roleHints().keySet().stream()
                    .filter(slotId -> formation.slot(slotId).isEmpty())
                    .forEach(slotId -> errors.add("preset " + preset.id() + " hint for unknown slot " + slotId));
            preset.slotTags().forEach((slotId, tagIds) ->
                    validatePresetSlot(preset.id(), formation, slotId, tagIds, tags, conflicts, errors));
        }
    }

    private void validatePresetSlot(String presetId, FormationDefinition formation, String slotId, List<String> tagIds,
                                    Map<String, BehaviorTagSeed> tags, Map<String, List<String>> conflicts,
                                    List<String> errors) {
        FormationSlot slot = formation.slot(slotId).orElse(null);
        if (Objects.isNull(slot)) {
            errors.add("preset " + presetId + " unknown slot " + slotId);
            return;
        }
        for (String tagId : tagIds) {
            BehaviorTagSeed tag = tags.get(tagId);
            if (Objects.isNull(tag)) {
                errors.add("preset " + presetId + " slot " + slotId + " unknown tag " + tagId);
            } else if (!tag.position().equals(slot.group())) {
                errors.add("preset " + presetId + " slot " + slotId + " tag " + tagId + " belongs to " + tag.position());
            }
            for (String other : tagIds) {
                if (conflicts.getOrDefault(tagId, List.of()).contains(other)) {
                    errors.add("preset " + presetId + " slot " + slotId + " conflicting tags " + tagId + "/" + other);
                }
            }
        }
    }


    private void checkAttr(String key, String context, List<String> errors) {
        if (Attr.fromKey(key).isEmpty()) {
            errors.add(context + " unknown attr " + key);
        }
    }

    private static List<String> orEmpty(List<String> values) {
        return Objects.isNull(values) ? List.of() : values;
    }
}
