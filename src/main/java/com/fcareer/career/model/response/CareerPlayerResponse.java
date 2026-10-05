package com.fcareer.career.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fcareer.catalog.model.response.PlayerSummaryResponse;
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
public class CareerPlayerResponse {
    private PlayerSummaryResponse player;
    private boolean onLoan;
    private Integer dynamicPotential;
    private Long valueEur;
    private Integer form;
    private Instant acquiredAt;
}
