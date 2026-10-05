package com.fcareer.career.model.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
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
public class UpdateCareerRequest {

    @Size(max = 60)
    private String name;

    @PositiveOrZero
    private Long budgetEur;

    @Positive
    private Integer season;
}
