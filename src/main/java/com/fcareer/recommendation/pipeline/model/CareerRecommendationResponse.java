package com.fcareer.recommendation.pipeline.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fcareer.catalog.model.response.PlayerSummaryResponse;
import com.fcareer.fitengine.service.FitEngineV3;
import com.fcareer.recommendation.pipeline.FeasibilityBucket;
import com.fcareer.recommendation.pipeline.FeasibilityBuckets;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CareerRecommendationResponse {

    private String position;
    /** Aday havuzunun SQL önskoruyla seçilen büyüklüğü ve tam puanlanan oyuncu sayısı. */
    private int poolSize;
    private int scored;
    /** Kullanıcının bu mevkideki mevcut en uyumlu oyuncusu (karşılaştırma tabanı); mevkide oyuncu yoksa boş. */
    private Current current;
    private List<Item> items;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Current {
        private Long playerId;
        private String playerName;
        private String roleId;
        private double currentFit;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Item {
        private PlayerSummaryResponse player;
        private String roleId;
        private String roleName;
        private String focus;
        private double currentFit;
        private Double levelFit;
        private String fitLabel;
        /** Bir sezon sonraki rol uyumu ve beklenen OVR (tanım: GrowthProjector). Potansiyel gizleme modunda (SCOUT_RANGE) boştur. */
        private Double futureFit;
        private Integer projectedRating;
        /** currentFit − mevkideki mevcut oyuncunun currentFit'i. */
        private Double deltaVsCurrent;
        private FeasibilityBucket bucket;
        private String bucketLabel;
        private int bucketPoints;
        private List<FeasibilityBuckets.Factor> bucketFactors;
        private List<FitEngineV3.Contribution> pros;
        private List<FitEngineV3.Contribution> cons;
        /** FREE, YOUNG, READY */
        private List<String> tags;
        /** Sıralama puanı: currentFit − kova cezası. */
        private double rankScore;
    }
}
