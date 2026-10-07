package com.fcareer.ut.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fcareer.fitengine.model.TeamSetup;
import com.fcareer.fitengine.model.WeaponState;
import com.fcareer.ut.model.UtMetaModels.MetaCard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class UtSquadModels {

    public static final int MAX_SLOTS = 14;

    private UtSquadModels() {
    }

    public record SlotInput(@NotBlank String slotId, @NotBlank String position, @NotNull Long playerId, String roleId) {
    }

    public record SquadEvaluateRequest(@NotEmpty @Size(max = MAX_SLOTS) List<@Valid SlotInput> slots, @Valid TeamSetup setup, Short gameVersionId) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SlotEvaluation(String slotId, String position, MetaCard card, String roleId, String roleName, Double roleFit,
                                 WeaponState badge, String badgeText, String reason, Double setupDelta) {
    }

    public record SquadEvaluation(List<SlotEvaluation> slots, double averageMeta, double averageOverall, boolean assumption) {
    }
}
