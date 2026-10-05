package com.fcareer.career.model.response;

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
public class TeamSummaryResponse {
    private Integer teamId;
    private String name;
    private String league;
    private Integer gender;
    private Integer overall;
    private Integer attack;
    private Integer midfield;
    private Integer defence;
    private int playerCount;
}
