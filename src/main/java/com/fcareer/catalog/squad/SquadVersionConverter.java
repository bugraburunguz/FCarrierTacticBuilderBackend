package com.fcareer.catalog.squad;

import com.fcareer.career.model.response.CareerSquadVersionResponse;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SquadVersionConverter {

    public static SquadVersionResponse toResponse(SquadVersion version, boolean latest) {
        return SquadVersionResponse.builder()
                .id(version.id())
                .versionNo(version.versionNo())
                .label(version.label())
                .gameBuild(version.gameBuild())
                .exportedAt(version.exportedAt())
                .playerCount(version.playerCount())
                .latest(latest)
                .build();
    }

    public static CareerSquadVersionResponse toCareerResponse(SquadVersion version, boolean latest) {
        return CareerSquadVersionResponse.builder()
                .id(version.id())
                .versionNo(version.versionNo())
                .label(version.label())
                .exportedAt(version.exportedAt())
                .latest(latest)
                .build();
    }
}
