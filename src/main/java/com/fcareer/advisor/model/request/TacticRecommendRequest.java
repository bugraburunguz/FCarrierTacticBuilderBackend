package com.fcareer.advisor.model.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TacticRecommendRequest {

    @NotNull
    private Long careerId;

    private String lockFormation;

    private String lockPreset;

    @Min(1)
    @Max(3)
    @Builder.Default
    private int topN = 2;

    @Pattern(regexp = "auto|differentFormation|differentPreset")
    @Builder.Default
    private String diversityMode = "auto";
}
