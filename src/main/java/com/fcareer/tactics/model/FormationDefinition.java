package com.fcareer.tactics.model;

import java.util.List;
import java.util.Optional;

public record FormationDefinition(String id, List<FormationSlot> slots) {

    public Optional<FormationSlot> slot(String slotId) {
        return slots.stream().filter(s -> s.slotId().equals(slotId)).findFirst();
    }
}
