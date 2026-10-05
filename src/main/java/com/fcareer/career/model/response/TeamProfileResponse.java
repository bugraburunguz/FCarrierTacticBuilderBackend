package com.fcareer.career.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fcareer.catalog.model.response.PlayerSummaryResponse;
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
public class TeamProfileResponse {
    private TeamSummaryResponse team;
    private Integer rivalTeamId;
    private String rivalTeamName;
    private Double averageAge;
    private List<String> strengths;
    private List<String> weaknesses;
    private List<String> culture;
    private List<PlayerSummaryResponse> topPlayers;
}
