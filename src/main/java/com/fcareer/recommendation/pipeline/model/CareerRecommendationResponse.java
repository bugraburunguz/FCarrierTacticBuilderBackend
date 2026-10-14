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
    /** Hedef rol/focus ve kaynağı: TACTIC | SQUAD | REQUEST | DEFAULT (alternativeRoles açıkken roleId boştur). */
    private String roleId;
    private String roleName;
    private String focus;
    private String roleSource;
    private String profile;
    /** Aday havuzu büyüklüğü ve tam puanlanan oyuncu sayısı. */
    private int poolSize;
    private int scored;
    /** Kullanıcı kulübünün gücü (en iyi 16 oyuncunun OVR ortalaması) ve ilk 11 OVR ortalaması: fizibilite referansı. */
    private Double userClubStrength;
    private Double userFirstElevenAverage;
    /** Kullanıcının bu mevkideki mevcut oyuncusu ve hedef roldeki currentFit'i; mevkide oyuncu yoksa boş. */
    private Current current;
    private List<Item> items;
    /** Yalnız includeLongShots=true iken: HAYAL kovasından en çok 3 oyuncu. */
    private List<Item> longShots;

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
        /** currentFit − mevkideki mevcut oyuncunun AYNI rol/focus'taki currentFit'i. */
        private Double deltaVsCurrent;
        /** Mevki dışı aday: "Mevki dışı (CDM→CB)"; doğal mevkili ise boş. */
        private String positionLabel;
        private Double clubStrength;
        private Double clubStrengthGap;
        private Double ovrGap;
        private FeasibilityBucket bucket;
        private String bucketLabel;
        private int bucketPoints;
        private List<FeasibilityBuckets.Factor> bucketFactors;
        private List<FitEngineV3.Contribution> pros;
        private List<FitEngineV3.Contribution> cons;
        /** FREE, YOUNG, READY */
        private List<String> tags;
        /** Sıralama puanı: (1−w)·currentFit + w·futureFit − kova cezası − yaş riski. */
        private double rankScore;
    }
}
