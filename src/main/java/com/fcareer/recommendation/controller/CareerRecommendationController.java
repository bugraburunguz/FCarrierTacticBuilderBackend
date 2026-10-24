package com.fcareer.recommendation.controller;

import com.fcareer.auth.security.AuthUser;
import com.fcareer.billing.model.response.CreditedResponse;
import com.fcareer.career.desk.SpoilerPolicy;
import com.fcareer.recommendation.pipeline.CareerRecommender;
import com.fcareer.recommendation.pipeline.model.CareerRecommendationRequest;
import com.fcareer.recommendation.pipeline.model.CareerRecommendationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.fcareer.common.constants.ApiConstants.API_V1;

/** Kariyer öneri hattı (E3-6): mevki için fizibilite kovalı, çeşitlendirilmiş ve açıklamalı öneriler. Ücretli uçtur (CreditGuard); sahiplik careerService'te doğrulanır. */
@RestController
@RequestMapping(API_V1 + "/careers/{careerId}/recommendations")
@RequiredArgsConstructor
public class CareerRecommendationController {

    private final CareerRecommender recommender;
    private final SpoilerPolicy spoilerPolicy;

    @PostMapping
    public CreditedResponse<CareerRecommendationResponse> recommend(@AuthenticationPrincipal AuthUser user, @PathVariable Long careerId,
                                                                    @Valid @RequestBody CareerRecommendationRequest request) {
        CreditedResponse<CareerRecommendationResponse> response = recommender.recommend(user.id(), careerId, request);
        boolean ranged = spoilerPolicy.ranged(careerId);
        response.getResult().getItems().forEach((item) -> spoilerPolicy.mask(ranged, item.getPlayer()));
        if (response.getResult().getLongShots() != null) {
            response.getResult().getLongShots().forEach((item) -> spoilerPolicy.mask(ranged, item.getPlayer()));
        }
        return response;
    }
}
