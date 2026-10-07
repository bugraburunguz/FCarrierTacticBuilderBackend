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
        private List<DevelopmentGap> gaps;
        private int totalGap;
        private double projectedScore;
        private WeaponState projectedBadge;
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
    }
}
