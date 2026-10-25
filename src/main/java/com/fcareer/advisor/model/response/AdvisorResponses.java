package com.fcareer.advisor.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fcareer.fitengine.model.response.SquadFitResponse;
import com.fcareer.recommendation.model.response.RecommendationItem;
import com.fcareer.tactics.model.request.TacticRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public final class AdvisorResponses {

    private AdvisorResponses() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Candidate {
        private String kind;
        private String presetId;
        private String name;
        private String formation;
        private String formationLabel;
        private double score;
        private double squadFit;
        private Double intentFit;
        private TacticRequest tactic;
        private SquadFitResponse lineup;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PositionOption {
        private String position;
        private String roleName;
        private double score;
        private boolean natural;
        private List<String> reasons;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PlayerPositionAdvice {
        private Long playerId;
        private String playerName;
        private List<String> listedPositions;
        private List<PositionOption> best;
        private String note;
        private boolean ownPositionBest;
        private String bestOverallPosition;
        private Integer bestOverall;
        /** FIT-3 madde 13: kanat mevkisinde taraf tarzla seçildiyse kısa gerekçe ("Orta profili baskın (Orta 78 · Bitiricilik 61) + sağ ayak → RW · Winger"). */
        private String sideReason;
        /** FIT-3 madde 12: rol bazlı dönüşüm önerisi (yoksa null). */
        private Conversion conversion;
    }

    /** FIT-3 madde 12: listede olmayan mevkide belirgin daha yüksek Güç (EA OVR'si yakınsa). */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Conversion {
        private String position;
        private String roleName;
        private double strength;
        private String fromPosition;
        private String fromRoleName;
        private double fromStrength;
        private Integer overallAt;
        private String planName;
        private String text;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TransferSuggestion {
        private String slotId;
        private String position;
        private String roleId;
        private List<RecommendationItem> items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Advice {
        private Candidate recommended;
        private String verdict;
        private List<String> reasons;
        private Candidate philosophy;
        private Candidate bestCustom;
        private List<Candidate> alternatives;
        private List<PlayerPositionAdvice> playerPositions;
        private List<TransferSuggestion> transferSuggestions;
        private int evaluated;
    }
}
