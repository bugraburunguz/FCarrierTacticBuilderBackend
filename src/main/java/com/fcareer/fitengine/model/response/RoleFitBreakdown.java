package com.fcareer.fitengine.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoleFitBreakdown {
    private double base;
    private double playstyleBonus;
    private double accelerate;
    private double weaponPenalty;
    private double outOfPosition;
    private double floorPenalty;
}
