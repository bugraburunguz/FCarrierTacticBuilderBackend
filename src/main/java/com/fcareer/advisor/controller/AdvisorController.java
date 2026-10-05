package com.fcareer.advisor.controller;

import com.fcareer.advisor.model.request.AdvisorRequest;
import com.fcareer.advisor.model.request.TacticRecommendRequest;
import com.fcareer.advisor.model.response.TacticRecommendation;
import com.fcareer.advisor.service.TacticRecommenderService;
import com.fcareer.advisor.model.response.AdvisorResponses.Advice;
import com.fcareer.advisor.model.response.AdvisorResponses.PlayerPositionAdvice;
import com.fcareer.advisor.service.AdvisorService;
import com.fcareer.advisor.service.PositionAdviceService;
import com.fcareer.auth.security.AuthUser;
import com.fcareer.billing.model.response.CreditedResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.fcareer.common.constants.ApiConstants.API_V1;

@RestController
@RequestMapping(API_V1 + "/advisor")
@RequiredArgsConstructor
public class AdvisorController {

    private final AdvisorService advisorService;
    private final PositionAdviceService positionAdviceService;
    private final TacticRecommenderService tacticRecommenderService;

    @PostMapping("/squad")
    public CreditedResponse<Advice> advise(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody AdvisorRequest request) {
        return advisorService.advise(user.id(), request);
    }

    @PostMapping("/tactic")
    public CreditedResponse<TacticRecommendation.Result> tactic(@AuthenticationPrincipal AuthUser user,
                                                                @Valid @RequestBody TacticRecommendRequest request) {
        return tacticRecommenderService.recommend(user.id(), request);
    }

    @GetMapping("/positions/{careerId}")
    public List<PlayerPositionAdvice> positions(@AuthenticationPrincipal AuthUser user, @PathVariable Long careerId) {
        return positionAdviceService.forCareer(user.id(), careerId);
    }
}
