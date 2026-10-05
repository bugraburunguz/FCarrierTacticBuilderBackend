package com.fcareer.career.model.request;

import com.fcareer.career.model.enums.RosterEventType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RosterEventRequest {

    @NotNull
    private RosterEventType type;

    @NotNull
    private Long playerId;

    @PositiveOrZero
    private Long feeEur;

    private Integer fromClubId;

    private Integer toClubId;
}
