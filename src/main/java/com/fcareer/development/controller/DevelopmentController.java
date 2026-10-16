package com.fcareer.development.controller;

import com.fcareer.development.model.DevelopmentModels.DevelopmentRequest;
import com.fcareer.development.model.DevelopmentModels.DevelopmentResponse;
import com.fcareer.development.model.DevelopmentModels.WonderkidQuery;
import com.fcareer.development.model.DevelopmentModels.WonderkidResponse;
import com.fcareer.development.service.DevelopmentService;
import com.fcareer.auth.security.AuthUser;
import com.fcareer.career.service.CareerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.fcareer.common.constants.ApiConstants.API_V1;

@RestController
@RequestMapping(API_V1)
@RequiredArgsConstructor
public class DevelopmentController {

    private final DevelopmentService developmentService;
    private final CareerService careerService;
    private final com.fcareer.career.desk.SpoilerPolicy spoilerPolicy;

    @PostMapping("/players/{id}/development")
    public DevelopmentResponse development(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @RequestBody(required = false) DevelopmentRequest request) {
        DevelopmentRequest body = request == null ? new DevelopmentRequest() : request;
        if (body.getCareerId() != null) {
            careerService.requireOwned(user == null ? null : user.id(), body.getCareerId());
        }
        return developmentService.plan(id, body);
    }

    @GetMapping("/wonderkids")
    public List<WonderkidResponse> wonderkids(
            @AuthenticationPrincipal AuthUser user,
            @RequestParam(name = "career", required = false) Long careerId,
            @RequestParam(name = "gv", required = false) Short gameVersionId,
            @RequestParam(name = "gender", required = false) Short gender,
            @RequestParam(name = "pos", required = false) String position,
            @RequestParam(name = "maxAge", required = false, defaultValue = "21") Integer maxAge,
            @RequestParam(name = "minPot", required = false) Integer minPotential,
            @RequestParam(name = "league", required = false) Integer leagueId,
            @RequestParam(name = "budgetMax", required = false) Long budgetMax,
            @RequestParam(name = "role", required = false) String roleId,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "limit", required = false, defaultValue = "0") int limit) {
        if (careerId != null) {
            careerService.requireOwned(user == null ? null : user.id(), careerId);
        }
        List<WonderkidResponse> rows = developmentService.wonderkids(WonderkidQuery.builder()
                .gameVersionId(gameVersionId).gender(gender).position(position).maxAge(maxAge)
                .minPotential(minPotential).leagueId(leagueId).budgetMax(budgetMax).careerId(careerId).userId(user == null ? null : user.id()).roleId(roleId)
                .sort(sort).limit(limit).build());
        if (spoilerPolicy.ranged(careerId)) {
            rows.forEach(r -> {
                r.setPotentialRange(com.fcareer.career.desk.SpoilerPolicy.range(r.getPotential()));
                r.setPotential(com.fcareer.career.desk.SpoilerPolicy.low(r.getPotential()));
                r.setGap(r.getPotential() - r.getOverall());
                r.setPotentialPerMillion(null);
            });
        }
        return rows;
    }
}
