package com.fcareer.fitengine.service;

import com.fcareer.fitengine.model.IntentProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Taktiği sağ-sol aynalar: sol/sağ eş slotların rolü ve davranışları yer değiştirir (ör. sağ kanat "orta aç" ise sol
 * kanat onu üstlenir). Slotların konumu/mevkisi aynı kalır, sadece görev devredilir.
 */
public final class TacticMirror {

    private static final Map<String, String> PAIRS = Map.ofEntries(
            Map.entry("LB", "RB"), Map.entry("LCB", "RCB"), Map.entry("LM", "RM"), Map.entry("LW", "RW"),
            Map.entry("LCM", "RCM"), Map.entry("LDM", "RDM"), Map.entry("LAM", "RAM"), Map.entry("LST", "RST"));

    private TacticMirror() {
    }

    public static String counterpart(String slotId) {
        String right = PAIRS.get(slotId);
        if (Objects.nonNull(right)) {
            return right;
        }
        return PAIRS.entrySet().stream().filter(e -> e.getValue().equals(slotId)).map(Map.Entry::getKey).findFirst().orElse(null);
    }

    public static boolean isMirrorable(List<IntentProfile> slots) {
        return slots.stream().anyMatch(s -> hasCounterpart(slots, s.getSlotId()) && differs(slots, s));
    }

    public static List<IntentProfile> mirror(List<IntentProfile> slots) {
        List<IntentProfile> mirrored = new ArrayList<>();
        for (IntentProfile slot : slots) {
            IntentProfile source = hasCounterpart(slots, slot.getSlotId())
                    ? slots.stream().filter(s -> s.getSlotId().equals(counterpart(slot.getSlotId()))).findFirst().orElse(slot)
                    : slot;
            mirrored.add(IntentProfile.builder()
                    .slotId(slot.getSlotId())
                    .slotPosition(slot.getSlotPosition())
                    .slotGroup(slot.getSlotGroup())
                    .tags(source.getTags())
                    .weaponBoost(source.getWeaponBoost())
                    .resolvedRole(source.getResolvedRole())
                    .build());
        }
        return mirrored;
    }

    private static boolean hasCounterpart(List<IntentProfile> slots, String slotId) {
        String other = counterpart(slotId);
        return Objects.nonNull(other) && slots.stream().anyMatch(s -> s.getSlotId().equals(other));
    }

    private static boolean differs(List<IntentProfile> slots, IntentProfile slot) {
        IntentProfile other = slots.stream().filter(s -> s.getSlotId().equals(counterpart(slot.getSlotId()))).findFirst().orElse(slot);
        return !other.getResolvedRole().getId().equals(slot.getResolvedRole().getId())
                || !other.getTags().stream().map(t -> t.getId()).toList().equals(slot.getTags().stream().map(t -> t.getId()).toList());
    }
}
