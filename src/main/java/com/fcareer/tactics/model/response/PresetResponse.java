package com.fcareer.tactics.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
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
public class PresetResponse {
    private String id;
    private String kind;
    private String name;
    private String club;
    private String coach;
    private String season;
    private String formation;
    private Map<String, Object> settings;
    private String signature;
    private Map<String, String> roleHints;
    private Map<String, List<String>> slotTags;
    private String sourceNote;
}
