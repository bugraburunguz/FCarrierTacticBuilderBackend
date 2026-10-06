package com.fcareer.fitengine.controller;

import com.fcareer.auth.security.AuthUser;
import com.fcareer.billing.model.response.CreditedResponse;
import com.fcareer.fitengine.model.request.CompareRequest;
import com.fcareer.fitengine.model.request.FitRoleRequest;
import com.fcareer.fitengine.model.request.SquadFitRequest;
import com.fcareer.fitengine.model.response.FitRoleResult;
import com.fcareer.fitengine.model.response.SquadFitResponse;
import com.fcareer.fitengine.service.FitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.fcareer.common.constants.ApiConstants.API_V1;

@RestController
@RequestMapping(API_V1)
@RequiredArgsConstructor
public class FitController {

    private final FitService fitService;

    @PostMapping("/fit/role")
    public FitRoleResult roleFit(@Valid @RequestBody FitRoleRequest request) {
        return fitService.roleFit(request);
    }

    @PostMapping("/players/compare")
    public List<FitRoleResult> compare(@Valid @RequestBody CompareRequest request) {
        return fitService.compare(request);
    }

    @PostMapping("/fit/lineup")
    public SquadFitResponse lineup(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody SquadFitRequest request) {
        return fitService.lineup(user.id(), request);
    }

    @PostMapping("/fit/squad")
    public CreditedResponse<SquadFitResponse> squadFit(@AuthenticationPrincipal AuthUser user,
                                                       @Valid @RequestBody SquadFitRequest request) {
        return fitService.squadFit(user.id(), request);
    }
}
