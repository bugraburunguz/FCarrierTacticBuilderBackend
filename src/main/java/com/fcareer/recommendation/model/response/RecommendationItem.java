package com.fcareer.recommendation.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fcareer.catalog.model.response.PlayerSummaryResponse;
import com.fcareer.fitengine.model.response.IntentFitResponse;
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
public class RecommendationItem {
    private PlayerSummaryResponse player;
    private RoleFitResponse roleFit;
    private IntentFitResponse intentFit;
    private double combined;
    private Double deltaVsCurrent;
    private Double setupDelta;
}
