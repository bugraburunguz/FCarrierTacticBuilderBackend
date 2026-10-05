package com.fcareer.career.model.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
public class CreateCareerRequest {

    private Short gameVersionId;

    @NotNull
    private Integer clubId;

    @PositiveOrZero
    private Long budgetEur;

    @Positive
    private Integer season;
}
