package com.fcareer.ut.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public final class SbcModels {

    public static final int MAX_TARGET = 3_000_000;
    public static final int MAX_POOL_ENTRIES = 120;

    private SbcModels() {
    }

    public enum Rarity {
        REGULAR, TOTW, HERO, ICON
    }

    public record ScoreSeed(String version, String note, Map<Integer, Integer> regular, List<FloorBand> regularFloor,
                            Map<Integer, Integer> totw, Map<Integer, Integer> icon, Map<Integer, Integer> hero) {
    }

    public record FloorBand(int min, int max, int score) {
    }

    public record PoolEntry(@Min(45) @Max(99) int rating, @NotNull Rarity rarity, @Min(1) @Max(500) int count, boolean untradeable,
                            @PositiveOrZero Long priceEach) {
    }

    public record StreamlinedRequest(@Min(1) @Max(MAX_TARGET) int targetScore, @Min(45) @Max(99) Integer minRating,
                                     @NotEmpty @Size(max = MAX_POOL_ENTRIES) List<@Valid PoolEntry> pool) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Pick(int rating, Rarity rarity, boolean untradeable, int count, int scoreEach, Long priceEach, long totalScore, long totalCost) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StreamlinedResult(boolean feasible, int targetScore, long totalScore, long overshoot, long totalCost, int itemCount,
                                    boolean coinless, boolean approximate, List<Pick> picks, Long maxReachableScore, String note) {
    }

    public record ScoreRow(int rating, Rarity rarity, int score) {
    }

    public record ScoreTable(String version, String note, List<ScoreRow> rows) {
    }
}
