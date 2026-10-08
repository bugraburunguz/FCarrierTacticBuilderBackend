package com.fcareer.recommendation.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
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
public class ScoutingResponse {
    private Double squadAverage;
    private Long budgetEur;
    private int hiddenUnrealistic;
    private List<ScoutingCandidate> items;
}
