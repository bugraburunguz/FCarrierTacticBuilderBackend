package com.fcareer.catalog.controller;

import com.fcareer.catalog.mapper.PlayerMapper;
import com.fcareer.catalog.model.request.PlayerSearchRequest;
import com.fcareer.catalog.model.response.PlayerDetailResponse;
import com.fcareer.catalog.model.response.PlayerSummaryResponse;
import com.fcareer.catalog.service.PlayerService;
import com.fcareer.common.exception.InvalidTacticException;
import com.fcareer.common.model.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.fcareer.common.constants.ApiConstants.API_V1;

@RestController
@RequestMapping(API_V1 + "/players")
@RequiredArgsConstructor
public class PlayerController {

    private static final String MIN_SUFFIX = "_min";
    private static final String MAX_SUFFIX = "_max";
    private static final String ORDER_DESC = "desc";

    private final PlayerService playerService;

    @GetMapping
    public PageResponse<PlayerSummaryResponse> search(
            @RequestParam(name = "gv", required = false) Short gameVersionId,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "pos", required = false) List<String> positions,
            @RequestParam(name = "ps", required = false) List<String> playstyles,
            @RequestParam(name = "at", required = false) List<String> accelerate,
            @RequestParam(name = "ovr_min", required = false) Integer overallMin,
            @RequestParam(name = "ovr_max", required = false) Integer overallMax,
            @RequestParam(name = "pot_min", required = false) Integer potentialMin,
            @RequestParam(name = "value_max", required = false) Long valueMax,
            @RequestParam(name = "club", required = false) Integer clubId,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "order", required = false, defaultValue = ORDER_DESC) String order,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "0") int size,
            @RequestParam Map<String, String> allParams) {
        Map<String, Integer> attrMin = new HashMap<>();
        Map<String, Integer> attrMax = new HashMap<>();
        allParams.forEach((key, value) -> collectAttrBound(key, value, attrMin, attrMax));
        return playerService.search(PlayerSearchRequest.builder()
                .gameVersionId(gameVersionId)
                .query(query)
                .positions(orEmpty(positions))
                .playstyles(orEmpty(playstyles))
                .accelerate(orEmpty(accelerate))
                .attrMin(attrMin)
                .attrMax(attrMax)
                .overallMin(overallMin)
                .overallMax(overallMax)
                .potentialMin(potentialMin)
                .valueMax(valueMax)
                .clubId(clubId)
                .sort(sort)
                .ascending(!ORDER_DESC.equalsIgnoreCase(order))
                .page(page)
                .size(size)
                .build());
    }

    @GetMapping("/{id}")
    public PlayerDetailResponse detail(@PathVariable Long id,
                                       @RequestParam(name = "gv", required = false) Short gameVersionId) {
        return playerService.detail(id, gameVersionId);
    }

    private void collectAttrBound(String key, String value, Map<String, Integer> attrMin, Map<String, Integer> attrMax) {
        boolean min = key.endsWith(MIN_SUFFIX);
        boolean max = key.endsWith(MAX_SUFFIX);
        if (!min && !max) {
            return;
        }
        String attrName = key.substring(0, key.length() - MIN_SUFFIX.length());
        if (attrName.equals("ovr") || attrName.equals("pot") || attrName.equals("value")) {
            return;
        }
        try {
            String normalized = PlayerMapper.normalizeAttr(attrName).getKey();
            (min ? attrMin : attrMax).put(normalized, Integer.parseInt(value));
        } catch (IllegalArgumentException e) {
            throw new InvalidTacticException("filter " + key);
        }
    }

    private static <T> List<T> orEmpty(List<T> values) {
        return values == null ? new ArrayList<>() : values;
    }
}
