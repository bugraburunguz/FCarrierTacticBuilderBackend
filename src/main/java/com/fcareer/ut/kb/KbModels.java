package com.fcareer.ut.kb;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

public final class KbModels {

    private KbModels() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    /** firstSeenAt: set ilk görüldüğünde; contentChangedAt: içerik özeti son değiştiğinde (yoksa alan yok). Rozet kuralları web'de. */
    public record SbcSetDto(long id, String name, String description, String category, Integer repeatable, Instant startsAt, Instant endsAt,
                            JsonNode reward, String status, int challengeCount, List<SbcChallengeDto> challenges, Instant firstSeenAt, Instant contentChangedAt) {

        SbcSetDto withChallenges(int count, List<SbcChallengeDto> list) {
            return new SbcSetDto(id, name, description, category, repeatable, startsAt, endsAt, reward, status, count, list, firstSeenAt, contentChangedAt);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SbcChallengeDto(long id, long setId, String name, String formation, String kind, JsonNode requirements, Integer itemScoreTarget, JsonNode reward) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ObjectiveGroupDto(long id, String name, String gameMode, Instant startsAt, Instant endsAt, JsonNode reward, JsonNode objectives, String status) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EvolutionDto(long id, String name, JsonNode cost, JsonNode requirements, JsonNode levels, Instant startsAt, Instant endsAt, String status) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PromoDto(int id, String code, String name, Instant startsAt, Instant endsAt) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ItemDto(long id, long eaAssetId, long eaResourceId, String name, int rating, String position, List<String> altPositions,
                          Integer clubId, Integer leagueId, Integer nationId, String cardType, boolean rare, String promo, JsonNode faceStats,
                          Integer weakFoot, Integer skillMoves, String accelerate, boolean untradeableOnly, String status, String quality) {
    }

    public record PricePoint(Instant bucket, Integer p10, Integer p50, Integer minBin, int samples, boolean lowConfidence) {
    }

    public record ContributionResult(int created, int updated, int unchanged, int queued, int pricesRecorded, int pricesSkipped) {
    }

    public record QueueItem(long id, String kind, String naturalKey, JsonNode candidates, Instant createdAt) {
    }

    /** resourceIds: EA kaynak kimlikleri (ör. SBC ödül öğeleri); boşsa süzülmez. */
    public record ItemSearch(String q, Integer minRating, Integer maxRating, String position, Integer leagueId, Integer nationId, Integer clubId, String cardType,
                             List<Long> resourceIds, int limit, int offset) {
    }
}
