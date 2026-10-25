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
    /** FIT-2 madde 6: istenirse (/fit/lineup roleAlternatives=true) slot mevkisinin bütün rolleri bu oyuncuya göre, en uygun önce. */
    private List<RoleAlternative> roleAlternatives;
    /** FIT-2 madde 6: en iyi rol mevcut rolden en az `fcareer.fit.role-suggest.min-gain` puan oturuyorsa o rol. */
    private RoleAlternative betterRole;
    private IntentFitResponse intentFit;
    private List<DepthEntry> depth;
}
