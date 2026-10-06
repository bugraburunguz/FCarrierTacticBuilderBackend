package com.fcareer.recommendation.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SlotViewRequest {

    @NotNull
    private Long careerId;

    @NotNull
    private SlotScope scope;

    @Valid
    @NotNull
    private RecommendSlotRequest slot;
}
