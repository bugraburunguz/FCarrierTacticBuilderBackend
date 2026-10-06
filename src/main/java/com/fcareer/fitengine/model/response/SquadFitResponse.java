package com.fcareer.fitengine.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SquadFitResponse {
    private double squadFit;
    private Double intentFit;
    private String summary;
    private List<SlotAssignment> slots;
    private List<WeakLink> weakLinks;
    private List<RuleFinding> rules;
    private String attackPattern;
    private List<Long> bench;
    private boolean mirrored;
}
