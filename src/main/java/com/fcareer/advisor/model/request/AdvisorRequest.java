package com.fcareer.advisor.model.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
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
public class AdvisorRequest {

    @NotNull
    private Long careerId;

    private String philosophyPresetId;

    @Min(0)
    @Max(100)
    @Builder.Default
    private int philosophyWeight = 40;

    private boolean allowTransfers;
}
