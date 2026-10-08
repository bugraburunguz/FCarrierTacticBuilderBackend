package com.fcareer.catalog.model.response;

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
public class PlayerSummaryResponse {
    private Long id;
    private String name;
    private Integer age;
    private int overall;
    private List<String> positions;
    private Integer clubId;
    private String club;
    private String league;
    private String nationality;
    private Integer gender;
    private String preferredFoot;
    private Integer weakFoot;
    private Integer skillMoves;
    private String accelerate;
    private Integer runStyle;
    private Integer potential;
    private Long valueEur;
    private boolean valuationEstimated;
    private boolean freeAgent;
    private Long faceAssetId;
    private String faceUrl;
    private Boolean changed;
    private com.fcareer.versioning.model.VersionModels.ChangeSummary changeSummary;
}
