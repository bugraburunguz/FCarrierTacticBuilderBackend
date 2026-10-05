package com.fcareer.catalog.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlayerDetailResponse {
    private PlayerSummaryResponse summary;
    private String preferredFoot;
    private Integer heightCm;
    private Integer weightKg;
    private Integer weakFoot;
    private Integer skillMoves;
    private Map<String, Integer> attrs;
    private Map<String, Integer> playstyles;
}
