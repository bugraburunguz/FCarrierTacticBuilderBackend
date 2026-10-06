package com.fcareer.tacticcode.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EaCodecData(String alphabet, Map<String, String> substitutions, Map<String, String> positionTables,
                          List<Role> roles, List<Formation> formations) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Role(int id, String name, String position, List<String> focuses) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Formation(String id, String label, List<String> positions, List<Integer> roleIds, List<Integer> base) {
    }
}
