package com.fcareer.recommendation.model.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendSlotRequest {

    private Long careerId;

    @NotBlank
    private String roleId;

    @NotBlank
    private String position;

    @Size(max = 6)
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    @PositiveOrZero
    private Long budgetMax;

    private Short gameVersionId;

    private Long currentPlayerId;

    @Max(25)
    private Integer limit;

    @Builder.Default
    private boolean includeUnvalued = true;
}
