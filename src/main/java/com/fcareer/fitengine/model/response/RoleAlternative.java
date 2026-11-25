package com.fcareer.fitengine.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** FIT-2 madde 6: bir oyuncunun slottaki bir rol/focus'taki Güç (currentFit) ve Rol oturması (levelFit; norm yoksa null). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoleAlternative {
    private String roleId;
    private String roleName;
    private double currentFit;
    private Double levelFit;
}
