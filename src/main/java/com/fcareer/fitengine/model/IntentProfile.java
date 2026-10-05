package com.fcareer.fitengine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
@AllArgsConstructor
public class IntentProfile {
    private final String slotId;
    private final String slotPosition;
    private final String slotGroup;
    private final List<FitTag> tags;
    private final Map<Attr, Double> weaponBoost;
    private final FitRole resolvedRole;

    public boolean hasTags() {
        return tags != null && !tags.isEmpty();
    }

    public boolean hasTag(String tagId) {
        return hasTags() && tags.stream().anyMatch(t -> t.getId().equals(tagId));
    }
}
