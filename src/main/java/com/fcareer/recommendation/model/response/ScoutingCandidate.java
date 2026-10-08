package com.fcareer.recommendation.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fcareer.catalog.model.response.PlayerSummaryResponse;
import com.fcareer.fitengine.model.response.RoleFitResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScoutingCandidate {
    private PlayerSummaryResponse player;
    private RoleFitResponse roleFit;
    private double combined;
    private Acquisition acquisition;
    private Feasibility feasibility;
    private Long estimatedFee;
    private String valueSource;
    private Double valueRatio;
    private double opportunity;
    private boolean potential;
}
