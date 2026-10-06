package com.fcareer.catalog.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimilarPlayerResponse {
    private PlayerSummaryResponse player;
    private double similarity;
}
