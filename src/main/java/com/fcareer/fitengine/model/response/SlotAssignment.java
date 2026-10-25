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
public class SlotAssignment {
    private String slotId;
    private String position;
    private String roleId;
    private String roleName;
    private List<String> tags;
    private Long playerId;
    private String playerName;
    private Integer overall;
    private Double combined;
    private Double setupDelta;
    private RoleFitResponse roleFit;
    /** FIT-1 madde 2: iki eksenli zayıf halka sınıfı (KALITE_ACIGI, ROL_UYUMSUZ, IKISI, UYGUN); yalnız /fit/lineup, oyuncu dizili slotlarda. */
    private String weakClass;
    private IntentFitResponse intentFit;
    private List<DepthEntry> depth;
}
