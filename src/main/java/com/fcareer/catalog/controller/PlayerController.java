package com.fcareer.catalog.controller;

import com.fcareer.auth.security.AuthUser;
import com.fcareer.career.service.CareerService;
import com.fcareer.catalog.mapper.PlayerMapper;
import com.fcareer.catalog.model.request.PlayerSearchRequest;
import com.fcareer.catalog.model.response.PlayerDetailResponse;
import com.fcareer.catalog.model.response.PlayerSummaryResponse;
import com.fcareer.catalog.service.PlayerService;
import com.fcareer.common.exception.InvalidTacticException;
import com.fcareer.common.model.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.fcareer.common.constants.ApiConstants.API_V1;

@RestController
@RequestMapping(API_V1 + "/players")
@RequiredArgsConstructor
public class PlayerController {

    private static final String MIN_SUFFIX = "_min";
    private static final String MAX_SUFFIX = "_max";
    private static final String ORDER_DESC = "desc";
    private static final Set<String> RESERVED_BOUNDS = Set.of("ovr", "pot", "value", "age", "wf", "sm");

    private final PlayerService playerService;
    private final CareerService careerService;

    @GetMapping
    public PageResponse<PlayerSummaryResponse> search(
            @AuthenticationPrincipal AuthUser user,
            @RequestParam(name = "career", required = false) Long careerId,
            @RequestParam(name = "team", required = false) Integer teamId,
            @RequestParam(name = "free_agent", required = false, defaultValue = "false") boolean freeAgent,
            @RequestParam(name = "gv", required = false) Short gameVersionId,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "pos", required = false) List<String> positions,
            @RequestParam(name = "ps", required = false) List<String> playstyles,
            @RequestParam(name = "at", required = false) List<String> accelerate,
            @RequestParam(name = "ovr_min", required = false) Integer overallMin,
            @RequestParam(name = "ovr_max", required = false) Integer overallMax,
            @RequestParam(name = "pot_min", required = false) Integer potentialMin,
            @RequestParam(name = "pot_max", required = false) Integer potentialMax,
            @RequestParam(name = "value_max", required = false) Long valueMax,
            @RequestParam(name = "age_min", required = false) Integer ageMin,
            @RequestParam(name = "age_max", required = false) Integer ageMax,
            @RequestParam(name = "wf_min", required = false) Integer weakFootMin,
            @RequestParam(name = "sm_min", required = false) Integer skillMovesMin,
            @RequestParam(name = "foot", required = false) String foot,
            @RequestParam(name = "club", required = false) Integer clubId,
            @RequestParam(name = "league", required = false) Integer leagueId,
            @RequestParam(name = "nat", required = false) Integer nationalityId,
            @RequestParam(name = "gender", required = false) Short gender,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "order", required = false, defaultValue = ORDER_DESC) String order,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "0") int size,
            @RequestParam Map<String, String> allParams) {
        if (careerId != null) {
            careerService.requireOwned(user == null ? null : user.id(), careerId);
        }
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
                .potentialMax(potentialMax)
                .valueMax(valueMax)
                .ageMin(ageMin)
                .ageMax(ageMax)
                .weakFootMin(weakFootMin)
                .skillMovesMin(skillMovesMin)
                .foot(foot)
                .clubId(clubId)
                .careerId(careerId)
                .teamId(teamId)
                .freeAgent(freeAgent)
                .leagueId(leagueId)
                .nationalityId(nationalityId)
                .gender(gender)
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
        if (RESERVED_BOUNDS.contains(attrName)) {
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
