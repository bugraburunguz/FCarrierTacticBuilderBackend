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
public class ImportSummaryResponse {
    private int players;
    private int unmatchedPlayers;
    private int teams;
    private int freeAgents;
    private Integer guessedTeamId;
    private String guessedTeamName;
}
