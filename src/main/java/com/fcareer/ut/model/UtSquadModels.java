package com.fcareer.ut.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fcareer.fitengine.model.TeamSetup;
import com.fcareer.fitengine.model.WeaponState;
import com.fcareer.ut.model.UtMetaModels.MetaCard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public final class UtSquadModels {

    public static final int MAX_SLOTS = 18;

    private UtSquadModels() {
    }

    public record InlineCard(String name, @Min(1) @Max(99) int overall, List<String> positions, @NotEmpty Map<String, @Min(0) @Max(99) Integer> attrs) {
    }

    public record SlotInput(@NotBlank String slotId, @NotBlank String position, Long playerId, String roleId, @Valid InlineCard card) {
    }

    public record SquadEvaluateRequest(@NotEmpty @Size(max = MAX_SLOTS) List<@Valid SlotInput> slots, @Valid TeamSetup setup, Short gameVersionId) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SlotEvaluation(String slotId, String position, MetaCard card, String roleId, String roleName, Double roleFit,
                                 WeaponState badge, String badgeText, String reason, Double setupDelta) {
    }

    public record SquadEvaluation(List<SlotEvaluation> slots, double averageMeta, double averageOverall, int squadRating, boolean assumption) {
    }
}
