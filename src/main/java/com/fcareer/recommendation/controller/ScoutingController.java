package com.fcareer.recommendation.controller;

import com.fcareer.auth.security.AuthUser;
import com.fcareer.recommendation.model.request.ScoutingSearchRequest;
import com.fcareer.recommendation.model.response.ScoutingResponse;
import com.fcareer.recommendation.service.ScoutingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.fcareer.common.constants.ApiConstants.API_V1;

@RestController
@RequestMapping(API_V1 + "/scouting")
@RequiredArgsConstructor
public class ScoutingController {

    private final ScoutingService scoutingService;

    @PostMapping("/{careerId}/search")
    public ScoutingResponse search(@AuthenticationPrincipal AuthUser user, @PathVariable Long careerId,
                                   @Valid @RequestBody ScoutingSearchRequest request) {
        return scoutingService.search(user.id(), careerId, request);
    }
}
