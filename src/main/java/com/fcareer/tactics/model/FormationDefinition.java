package com.fcareer.tactics.model;

import java.util.List;
import java.util.Optional;

public record FormationDefinition(String id, String label, String eaFormationId, String eaLabel, String futggName, String displayNameTr, String displayNameEn,
                                  Usage usage, List<FormationSlot> slots) {

    /** FUT.GG kullanım yüzdeleri (topluluk / pro); admin tarihli günceller, otomatik çekilmez. */
    public record Usage(double community, double pro, String asOf) {
    }

    public Optional<FormationSlot> slot(String slotId) {
        return slots.stream().filter(s -> s.slotId().equals(slotId)).findFirst();
    }

    public String displayName() {
        return displayNameTr != null ? displayNameTr : label != null ? label : id;
    }
}
