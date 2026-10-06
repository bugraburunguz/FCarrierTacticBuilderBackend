package com.fcareer.fitengine.model.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SetupCheckRequest {

    @NotNull
    private Long careerId;

    @NotBlank
    private String buildUp;

    @NotNull
    @Min(0)
    @Max(100)
    private Integer depth;
}
