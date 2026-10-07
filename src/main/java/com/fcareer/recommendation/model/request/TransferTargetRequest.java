package com.fcareer.recommendation.model.request;

import com.fcareer.fitengine.model.TeamSetup;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
public class TransferTargetRequest {

    @NotNull
    private Long careerId;

    @Valid
    @NotEmpty
    @Size(max = 14)
    private List<TargetSlot> slots = new ArrayList<>();

    @Min(1)
    @Max(4)
    private Integer perPosition;

    @Valid
    private TeamSetup setup;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TargetSlot {

        @NotBlank
        private String position;

        @NotBlank
        private String roleId;

        @Size(max = 6)
        private List<String> tags = new ArrayList<>();
    }
}
