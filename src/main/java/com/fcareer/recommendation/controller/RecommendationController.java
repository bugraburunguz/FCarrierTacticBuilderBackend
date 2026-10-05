package com.fcareer.recommendation.controller;

import com.fcareer.auth.security.AuthUser;
import com.fcareer.billing.model.response.CreditedResponse;
import com.fcareer.recommendation.model.request.RecommendSlotRequest;
import com.fcareer.recommendation.model.response.RecommendationResponse;
import com.fcareer.recommendation.service.RecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.fcareer.common.constants.ApiConstants.API_V1;

@RestController
@RequestMapping(API_V1 + "/recommend")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @PostMapping("/preview")
    public RecommendationResponse preview(@Valid @RequestBody RecommendSlotRequest request) {
        return recommendationService.preview(request);
    }

    @PostMapping("/slot")
    public CreditedResponse<RecommendationResponse> slot(@AuthenticationPrincipal AuthUser user,
                                                         @Valid @RequestBody RecommendSlotRequest request) {
        return recommendationService.recommend(user.id(), request);
    }
}
