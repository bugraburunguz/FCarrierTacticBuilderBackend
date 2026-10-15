package com.fcareer.recommendation.pipeline.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;

/**
 * V3-06b: kariyer oyuncu sayfasının "Sözleşme ve değer" sekmesi. Kayıtta olmayan alan yanıtta yoktur (0'a çevrilmez, ENG-02);
 * kova ve alım sonrası bütçe yalnız kadroda olmayan oyuncu için, alım sonrası bütçe ayrıca bütçe ve değer biliniyorsa.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CareerPlayerContractResponse {
    boolean inSquad;
    Long valueEur;
    boolean valuationEstimated;
    Long wageEur;
    Long releaseClauseEur;
    LocalDate contractUntil;
    LocalDate gameDate;
    /** Oyun tarihine göre kalan tam ay (30 günlük ay, öneri hattıyla aynı). */
    Integer monthsLeft;
    boolean onLoan;
    String feasibility;
    Long budgetEur;
    Long budgetAfterPurchaseEur;
}
