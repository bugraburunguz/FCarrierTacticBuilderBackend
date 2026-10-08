package com.fcareer.ut.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public final class UtCaptureModels {

    public static final int MAX_CAPTURES = 2000;

    private UtCaptureModels() {
    }

    public record Capture(String url, Integer status, Long ts, String body) {
    }

    public record CaptureRequest(String linkToken, @NotNull @Size(max = MAX_CAPTURES) List<@Valid Capture> captures) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CaptureCard(long instanceId, long assetId, String name, int rating, String position, List<String> positions, String rarity,
                              Integer rarityId, String cardType, int teamId, int leagueId, int nation, Integer gender, boolean untradeable,
                              List<String> faceLabels, List<Integer> faceStats, Integer skillMoves, Integer weakFoot, Integer preferredFoot,
                              Integer playStyle, List<Integer> baseTraits, List<Integer> iconTraits, List<Integer> plusRoles,
                              Long lastSalePrice, Long marketMin, Long marketMax, Long marketAverage, boolean duplicate, String pile,
                              Long playerId, String club, String league, String nationality, Map<String, Integer> attrs) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SquadSlot(int index, boolean starter, String position, Integer chemistry, CaptureCard card) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ActiveSquad(String formation, String formationLabel, Integer chemistry, Integer rating, String name, List<SquadSlot> slots) {
    }

    public record SquadSummary(long id, String name, String formation, String formationLabel, Integer rating, Integer chemistry) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MarketPrice(String source, long assetId, String name, int rating, Long buyNow, Long currentBid, Long startingBid, Long lastSale,
                              Long expiresIn, CaptureCard card) {
    }

    public record Reward(String type, Long value, int count) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CaptureObjective(long id, String name, String description, int progress, int total, String status, int operator,
                                   List<Long> lockedBy, List<Reward> rewards, String stat) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ObjectiveGroupView(long groupId, String name, String gameMode, String status, Long startTime, Long endTime,
                                     List<Reward> rewards, List<CaptureObjective> objectives) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SbcSetView(long setId, String name, int categoryId, String category, int challengesCount, int challengesCompleted,
                             boolean repeatable, Long endTime, int awardCount) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SbcDetail(String path, JsonNode data) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EvolutionSlot(long id, String name, String status, Long endTime, int levels, int requirements) {
    }

    public record CurrencyView(String name, long funds) {
    }

    public record FormationConfig(int id, int index, String name, String label, List<Integer> positionSlots, List<String> positionNames) {
    }

    public record PositionConfig(int uniqueId, int typeId, String uniqueName, String typeName) {
    }

    public record StyleMod(int styleId, int style, int styleType, List<Integer> statBonus) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CaptureConfig(List<FormationConfig> formations, List<PositionConfig> positions, List<StyleMod> styleMods,
                                Map<String, List<Integer>> keyAttributesByPosition, JsonNode chemistry, Map<Integer, String> rarities) {
    }

    public record CaptureStats(int received, int processed, int stripped, int ignored, int invalid, Map<String, Integer> byKind,
                               int definitionNames, boolean definitionsTruncated, List<String> warnings) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CaptureResult(CaptureStats stats, List<CaptureCard> cards, ActiveSquad activeSquad, List<SquadSummary> squads,
                                List<MarketPrice> prices, List<ObjectiveGroupView> objectives, List<SbcSetView> sbcSets, List<SbcDetail> sbcDetails,
                                List<EvolutionSlot> evolutions, Long coins, List<CurrencyView> currencies, CaptureConfig config,
                                List<String> unknownPaths) {
    }
}
