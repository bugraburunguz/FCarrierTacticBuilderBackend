package com.fcareer.roles.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fcareer.roles.model.RolePlaystyles;
import com.fcareer.roles.model.RoleWeapon;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoleResponse {
    private String id;
    private String baseRole;
    private String name;
    private List<String> positions;
    private String focus;
    private Map<String, Double> attrWeights;
    private List<RoleWeapon> weapons;
    private RolePlaystyles playstyles;
    private List<String> accelerate;
    private Map<String, Integer> globalFloor;
    private List<String> movementTags;
}
