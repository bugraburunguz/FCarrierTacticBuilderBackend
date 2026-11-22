package com.fcareer.catalog.squad;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class SquadVersionResponse {
    private Long id;
    private Integer versionNo;
    private String label;
    private String gameBuild;
    private Instant exportedAt;
    private Integer playerCount;
    private boolean latest;
}
