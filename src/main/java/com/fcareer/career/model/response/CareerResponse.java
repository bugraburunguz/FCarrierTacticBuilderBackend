package com.fcareer.career.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
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
public class CareerResponse {
    private Long id;
    private String name;
    private Short gameVersionId;
    private Integer clubId;
    private String clubName;
    private Integer gender;
    private Integer season;
    private Long budgetEur;
    private int squadSize;
    private String spoilerMode;
    private Long wageBudgetEur;
    private java.time.LocalDate windowEndsOn;
    private java.time.LocalDate gameDate;
    private String challengeId;
    private Instant createdAt;
}
