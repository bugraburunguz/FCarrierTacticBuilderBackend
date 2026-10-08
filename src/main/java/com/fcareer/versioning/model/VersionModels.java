package com.fcareer.versioning.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class VersionModels {

    private VersionModels() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChangeSummary(ChangeType type, Integer ovrDelta, int count, Integer versionNo) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Delta(@JsonProperty("old") Integer oldValue, @JsonProperty("new") Integer newValue, Integer delta) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TextChange(@JsonProperty("old") String oldValue, @JsonProperty("new") String newValue) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AttrDiff(String key, @JsonProperty("old") int oldValue, @JsonProperty("new") int newValue, int delta) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PlayerDiff(Integer versionNo, Integer previousVersionNo, OffsetDateTime importedAt, ChangeType changeType, Delta overall,
                             Delta potential, TextChange positions, TextChange acceleRate, TextChange club, List<AttrDiff> attributes,
                             Map<String, Object> playStyles) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record VersionInfo(Long id, String scope, int versionNo, String source, String label, OffsetDateTime importedAt, int changeCount, boolean full) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChangedPlayer(Long playerId, String name, ChangeType type, Integer ovrOld, Integer ovrNew, String clubOld, String clubNew, int attributeChanges) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LatestChanges(VersionInfo version, Map<String, List<ChangedPlayer>> groups) {
    }
}
