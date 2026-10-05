package com.fcareer.career.mapper;

import com.fcareer.career.entity.CareerEntity;
import com.fcareer.career.entity.RosterEventEntity;
import com.fcareer.career.entity.UserPlayerStateEntity;
import com.fcareer.career.model.response.CareerPlayerResponse;
import com.fcareer.career.model.response.CareerResponse;
import com.fcareer.career.model.response.RosterEventResponse;
import com.fcareer.catalog.model.response.PlayerSummaryResponse;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class CareerMapper {

    public CareerResponse toResponse(CareerEntity career, String clubName, int squadSize) {
        return CareerResponse.builder()
                .id(career.getId())
                .gameVersionId(career.getGameVersionId())
                .clubId(career.getClubId())
                .clubName(clubName)
                .season(career.getSeason())
                .budgetEur(career.getBudgetEur())
                .squadSize(squadSize)
                .createdAt(career.getCreatedAt())
                .build();
    }

    public CareerPlayerResponse toPlayer(UserPlayerStateEntity state, PlayerSummaryResponse summary) {
        return CareerPlayerResponse.builder()
                .player(summary)
                .onLoan(state.isOnLoan())
                .dynamicPotential(Objects.isNull(state.getDynamicPotential()) ? null : state.getDynamicPotential().intValue())
                .valueEur(state.getValueEur())
                .form(Objects.isNull(state.getForm()) ? null : state.getForm().intValue())
                .acquiredAt(state.getAcquiredAt())
                .build();
    }

    public RosterEventResponse toEvent(RosterEventEntity event, Long budgetAfter) {
        return RosterEventResponse.builder()
                .id(event.getId())
                .type(event.getType())
                .playerId(event.getBasePlayerId())
                .feeEur(event.getFeeEur())
                .fromClubId(event.getFromClubId())
                .toClubId(event.getToClubId())
                .createdAt(event.getCreatedAt())
                .budgetEurAfter(budgetAfter)
                .build();
    }
}
