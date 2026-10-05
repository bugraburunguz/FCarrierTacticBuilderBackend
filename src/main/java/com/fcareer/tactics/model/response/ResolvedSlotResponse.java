package com.fcareer.tactics.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResolvedSlotResponse {
    private String slotId;
    private String position;
    private String group;
    private String roleId;
    private String roleName;
    private List<String> tags;
    private List<String> lockedTags;
}
