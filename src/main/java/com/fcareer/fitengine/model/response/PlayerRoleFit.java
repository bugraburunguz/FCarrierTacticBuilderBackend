package com.fcareer.fitengine.model.response;

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
public class PlayerRoleFit {
    private String position;
    private String roleId;
    private String roleName;
    private String focus;
    private double score;
    private int projectedRating;
    private WeaponState badge;
    private String badgeText;
    private List<String> reasons;
    private List<AttributeGap> gaps;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private Double currentFit;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private Double levelFit;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String engineVersion;
}
