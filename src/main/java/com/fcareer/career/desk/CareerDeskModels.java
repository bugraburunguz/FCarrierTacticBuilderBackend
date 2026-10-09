package com.fcareer.career.desk;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class CareerDeskModels {

    private CareerDeskModels() {
    }

    public static final int MAX_OVR_UPDATES = 600;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SnapshotDto(long id, Instant takenAt, Integer season, String transferWindow, String source, int playerCount, String note) {
    }

    /** kind: CHANGED (overall/potansiyel), TRANSFER (takım değişti), NEW (yeni oyuncu), REMOVED (dosyada yok). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DiffRow(long playerId, String name, String kind, Integer overallFrom, Integer overallTo, Integer potentialFrom, Integer potentialTo,
                          Integer teamFrom, Integer teamTo) {
    }

    public record DiffResult(Long fromSnapshot, Long toSnapshot, int changed, int transfers, int added, int removed, List<DiffRow> rows) {
    }

    public record HistoryPoint(long snapshotId, Instant takenAt, Integer season, int overall, int potential, Integer teamId) {
    }

    public record OvrUpdate(@NotNull Long playerId, @NotNull @Min(1) @Max(99) Integer overall, @Min(1) @Max(99) Integer potential) {
    }

    public record OvrBatch(@NotEmpty @Size(max = MAX_OVR_UPDATES) List<@Valid OvrUpdate> updates, @Size(max = 200) String note) {
    }

    public record TagRequest(@NotNull @Pattern(regexp = "LOCKED|ROTATION|DEVELOPING|FOR_SALE|LOAN_OK") String tag) {
    }

    public record TagDto(long playerId, String tag) {
    }

    public record ShortlistRequest(@Size(max = 300) String note, @Min(0) Long maxFeeEur) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ShortlistDto(long playerId, String note, Long maxFeeEur, Instant createdAt) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SeasonRecordDto(int season, Integer leaguePosition, List<String> trophies, Long topScorerId, String note) {
    }

    public record SeasonRecordRequest(@Min(0) Integer leaguePosition, @Size(max = 20) List<@Size(max = 60) String> trophies, Long topScorerId, @Size(max = 300) String note) {
    }

    /** Kariyer yedeği: elle girilen her şey (canlı oyuncu verisi Live Editor import'undan yeniden gelir). */
    public record Backup(int version, String name, Integer season, Long budgetEur, String spoilerMode, Long wageBudgetEur, String challengeId,
                         List<TagDto> tags, List<ShortlistDto> shortlist, List<SeasonRecordDto> seasons) {
    }
}
