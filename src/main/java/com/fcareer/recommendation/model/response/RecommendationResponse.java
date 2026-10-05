package com.fcareer.recommendation.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fcareer.fitengine.model.response.ScoutQuery;
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
public class RecommendationResponse {
    private String roleId;
    private String position;
    private Long budgetMax;
    private RecommendationItem current;
    private List<RecommendationItem> items;
    private ScoutQuery scoutQuery;
}
