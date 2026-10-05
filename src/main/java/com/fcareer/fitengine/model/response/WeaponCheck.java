package com.fcareer.fitengine.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fcareer.fitengine.model.WeaponState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WeaponCheck {
    private String attr;
    private int value;
    private int hardMin;
    private int effectiveMin;
    private int target;
    private boolean playstylePlusCompensated;
    private WeaponState state;
    private double penalty;
}
