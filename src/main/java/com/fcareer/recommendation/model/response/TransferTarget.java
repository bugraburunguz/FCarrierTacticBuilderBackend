package com.fcareer.recommendation.model.response;

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
public class TransferTarget {
    private PlayerSummaryResponse player;
    private RoleFitResponse roleFit;
    private double combined;
    private Acquisition acquisition;
    private String reason;
}
