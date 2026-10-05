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
public class WeakLink {
    private String slotId;
    private Long playerId;
    private String playerName;
    private String roleId;
    private double score;
    private WeaponState badge;
    private String sentence;
    private ScoutQuery scoutQuery;
}
