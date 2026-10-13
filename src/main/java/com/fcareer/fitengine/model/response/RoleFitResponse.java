package com.fcareer.fitengine.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fcareer.fitengine.model.WeaponState;
import com.fcareer.fitengine.service.FitEngineV3;
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
    /** v1 | v3. v3 alanları aşağıda; v1 yanıtında boştur. */
    private String engineVersion;
    private Double currentFit;
    private Double levelFit;
    /** v3: GOOD | AT_LEVEL | WEAK | WRONG_ROLE (levelFit bandı; badge yalnız GOOD/WEAK/WRONG_ROLE için dolu). */
    private String fitLabel;
    private Integer levelBandLo;
    private Integer levelBandHi;
    private Integer levelSample;
    private List<FitEngineV3.Contribution> contributions;
}
