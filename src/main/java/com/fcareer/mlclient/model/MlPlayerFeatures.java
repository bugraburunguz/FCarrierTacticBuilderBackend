package com.fcareer.mlclient.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MlPlayerFeatures {
    @JsonProperty("player_id")
    private Long playerId;
    private Integer age;
    private Integer overall;
    @JsonProperty("position_group")
    private String positionGroup;
    @JsonProperty("league_tier")
    private Integer leagueTier;
    @JsonProperty("intl_reputation")
    private Integer intlReputation;
    private Double pace;
    private Double shooting;
    private Double passing;
    private Double dribbling;
    private Double defending;
    private Double physical;
    @JsonProperty("weak_foot")
    private Integer weakFoot;
    @JsonProperty("skill_moves")
    private Integer skillMoves;
    @JsonProperty("is_gk")
    private Boolean goalkeeper;
    @JsonProperty("contract_years")
    private Integer contractYears;
}
