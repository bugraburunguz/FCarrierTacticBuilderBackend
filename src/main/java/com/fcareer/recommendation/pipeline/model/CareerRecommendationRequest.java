package com.fcareer.recommendation.pipeline.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Kariyer öneri isteği: mevki zorunlu; rol verilmezse mevkideki tüm rollerin en uyumlusu (aday başına) kullanılır. */
@Getter
@Setter
public class CareerRecommendationRequest {

    @NotBlank
    private String position;

    /** Opsiyonel taban rol (örn. "poacher") ya da bileşik kimlik ("poacher_attack"). */
    private String roleId;

    @Min(1)
    @Max(30)
    private Integer limit;

    private Integer ageMin;

    private Integer ageMax;
}
