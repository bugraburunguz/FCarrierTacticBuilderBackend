package com.fcareer.career.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fcareer.career.model.enums.RosterEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RosterEventResponse {
    private Long id;
    private RosterEventType type;
    private Long playerId;
    private Long feeEur;
    private Integer fromClubId;
    private Integer toClubId;
    private Instant createdAt;
    private Long budgetEurAfter;
}
