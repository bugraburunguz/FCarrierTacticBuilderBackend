package com.fcareer.recommendation.model.request;

import com.fcareer.fitengine.model.TeamSetup;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScoutingSearchRequest {

    @NotBlank
    private String position;

    @NotBlank
    private String roleId;

    @Size(max = 6)
    private List<String> tags = new ArrayList<>();

    @Valid
    private TeamSetup setup;

    @Min(15)
    @Max(45)
    private Integer ageMax;

    @Min(1)
    @Max(99)
    private Integer potentialMin;

    private boolean dream;

    @Size(max = 12)
    private String sort;

    @Min(1)
    @Max(50)
    private Integer limit;
}
