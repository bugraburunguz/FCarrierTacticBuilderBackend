package com.fcareer.catalog.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class PlayerSearchRequest {

    private Short gameVersionId;
    private String query;
    @Builder.Default
    private List<String> positions = new ArrayList<>();
    @Builder.Default
    private List<String> playstyles = new ArrayList<>();
    @Builder.Default
    private List<String> accelerate = new ArrayList<>();
    @Builder.Default
    private Map<String, Integer> attrMin = new HashMap<>();
    @Builder.Default
    private Map<String, Integer> attrMax = new HashMap<>();
    private Integer overallMin;
    private Integer overallMax;
    private Integer potentialMin;
    private Long valueMax;
    private Integer clubId;
    private Long idFilter;
    private String sort;
    private boolean ascending;
    private int page;
    private int size;
}
