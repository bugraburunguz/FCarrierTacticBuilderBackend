package com.fcareer.fitengine.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fcareer.fitengine.model.WeaponState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoleFitResponse {
    private Long playerId;
    private String playerName;
    private String roleId;
    private String roleName;
    private double score;
    private int projectedRating;
    private WeaponState badge;
    private String badgeText;
    private boolean outOfPosition;
    private List<String> reasons;
    private List<WeaponCheck> weaponChecks;
    private RoleFitBreakdown breakdown;
}
