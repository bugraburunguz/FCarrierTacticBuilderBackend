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
public class DepthEntry {
    private Long playerId;
    private String playerName;
    private double combined;
    private double roleFitScore;
    private WeaponState badge;
    /** v3: rozetin türediği seviye yüzdesi ve etiketi (RoleFitResponse ile aynı); ekran yüzdesi ve etiketi bundan (V3-07c). v2'de boş. */
    private Double levelFit;
    private String fitLabel;
}
