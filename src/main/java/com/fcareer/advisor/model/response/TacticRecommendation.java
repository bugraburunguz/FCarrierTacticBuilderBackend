package com.fcareer.advisor.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fcareer.fitengine.model.response.SquadFitResponse;
import com.fcareer.fitengine.model.response.WeakLink;
import com.fcareer.tactics.model.request.TacticRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public final class TacticRecommendation {

    private TacticRecommendation() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RankedItem {
        private String id;
        private String label;
        private int pct;
        private String note;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Combo {
        private String formation;
        private String formationLabel;
        private String presetId;
        private String presetName;
        private int pct;
        private double squadFit;
        private Double intentFit;
        private int feasibility;
        private List<String> reasons;
        private List<WeakLink> weakLinks;
        private TacticRequest tactic;
        private SquadFitResponse lineup;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Result {
        private List<RankedItem> formations;
        private List<RankedItem> presets;
        private List<Combo> bestCombos;
        private int evaluated;
    }
}
