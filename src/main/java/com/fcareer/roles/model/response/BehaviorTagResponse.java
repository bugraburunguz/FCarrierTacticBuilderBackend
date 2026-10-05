package com.fcareer.roles.model.response;

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
public class BehaviorTagResponse {
    private String id;
    private String position;
    private String label;
    private List<String> weaponAttrs;
    private List<String> playstyleWeapons;
    private int expectedMin;
    private String roleVote;
    private boolean modifier;
    private List<String> conflicts;
    private String nlg;
    private String accelerateHint;
}
