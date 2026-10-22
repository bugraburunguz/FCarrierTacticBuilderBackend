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
    private boolean loanedOut;
    private Integer dynamicPotential;
    private Long valueEur;
    private Integer form;
    private Instant acquiredAt;
    /** KARIYER-UX FAZ 2 (15 §7.3): kayıttaki kiralık bitişi (career_player.loan_until); bilinmiyorsa yok. */
    private java.time.LocalDate loanUntil;
    /** Kayıttaki haftalık maaş (career_player.wage_eur); bilinmiyorsa yok ("N oyuncunun maaşı bilinmiyor"). */
    private Long wageEur;
}
