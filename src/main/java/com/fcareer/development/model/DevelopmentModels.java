package com.fcareer.development.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fcareer.fitengine.model.WeaponState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public final class DevelopmentModels {

    private DevelopmentModels() {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DevelopmentRequest {
        private List<String> targetRoles;
        private Short gameVersionId;
        private Long careerId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DevelopmentGap {
        private String attr;
        private int value;
        private int needed;
        private int gap;
        private int maxGain;
        private boolean closable;
        private boolean hardToTrain;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleDevelopment {
        private String roleId;
        private String roleName;
        private String position;
        private String focus;
        private double currentScore;
        private WeaponState currentBadge;
        /** v3 (V3-07c): rozetin türediği seviye yüzdesi ve etiketi (GOOD|AT_LEVEL|WEAK|WRONG_ROLE); gösterilen yüzde bu. v2'de boş. */
        private Double currentLevelFit;
        private String currentFitLabel;
        private List<DevelopmentGap> gaps;
        private int totalGap;
        private double projectedScore;
        private WeaponState projectedBadge;
        private String projectedFitLabel;
        private int projectedRating;
        private boolean unlocks;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrainingPriority {
        private String attr;
        private int totalGap;
        private int roles;
        private boolean hardToTrain;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DevelopmentResponse {
        private Long playerId;
        private String playerName;
        private Integer age;
        private int overall;
        private Integer potential;
        private int headroom;
        private boolean estimated;
        private List<RoleDevelopment> perRole;
        private RoleDevelopment bestPath;
        private List<TrainingPriority> trainingPriorities;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WonderkidQuery {
        private Short gameVersionId;
        private Short gender;
        private String position;
        private Integer maxAge;
        private Integer minPotential;
        private Integer leagueId;
        private Long budgetMax;
        private Long careerId;
        /** Kariyer verildiğinde ulaşılabilirlik kovası için (sahiplik controller'da doğrulanır). */
        private Long userId;
        private String roleId;
        private String sort;
        private int limit;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class WonderkidResponse {
        private Long id;
        private String name;
        private Integer age;
        private int overall;
        private int potential;
        private String potentialRange;
        private int gap;
        private List<String> positions;
        private String club;
        private String league;
        private Long valueEur;
        private Double potentialPerMillion;
        private String roleId;
        private String roleName;
        private String rolePosition;
        private Double roleScore;
        private WeaponState roleBadge;
        private WeaponState projectedBadge;
        /** V3-07c 0a: rol rozetinin türediği seviye yüzdesi ve etiketi (gösterilen yüzde bu; roleScore ham rol puanıdır). */
        private Double roleLevelFit;
        private String roleFitLabel;
        private String projectedFitLabel;
        /** V3-07c: kariyerde ulaşılabilirlik kovası (oyuncu sayfasıyla aynı sınıflandırıcı); kariyersiz ve kadrodaki oyuncuda yok. */
        private String feasibility;
    }
}
