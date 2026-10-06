package com.fcareer.tactics.model.request;

import com.fcareer.fitengine.model.TeamSetup;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class TacticRequest {

    private String formation;

    private String presetId;

    @Valid
    private TeamSetup setup;

    @Valid
    @Size(max = 11)
    @Builder.Default
    private List<SlotSelection> slots = new ArrayList<>();
}
