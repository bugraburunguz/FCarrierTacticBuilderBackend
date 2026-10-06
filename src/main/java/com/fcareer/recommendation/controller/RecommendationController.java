package com.fcareer.recommendation.controller;

import com.fcareer.auth.security.AuthUser;
import com.fcareer.billing.model.response.CreditedResponse;
import com.fcareer.recommendation.model.request.RecommendSlotRequest;
import com.fcareer.recommendation.model.request.SlotViewRequest;
import com.fcareer.recommendation.model.request.TransferTargetRequest;
import com.fcareer.recommendation.model.response.TransferTargetsResponse;
import com.fcareer.recommendation.service.TransferTargetService;
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
    private final TransferTargetService transferTargetService;

    @PostMapping("/preview")
    public RecommendationResponse preview(@Valid @RequestBody RecommendSlotRequest request) {
        return recommendationService.preview(request);
    }

    @PostMapping("/slot-view")
    public RecommendationResponse slotView(@AuthenticationPrincipal AuthUser user,
                                           @Valid @RequestBody SlotViewRequest request) {
        return recommendationService.slotView(user.id(), request);
    }

    @PostMapping("/transfer-targets")
    public TransferTargetsResponse transferTargets(@AuthenticationPrincipal AuthUser user,
                                                   @Valid @RequestBody TransferTargetRequest request) {
        return transferTargetService.targets(user.id(), request);
    }

    @PostMapping("/slot")
    public CreditedResponse<RecommendationResponse> slot(@AuthenticationPrincipal AuthUser user,
                                                         @Valid @RequestBody RecommendSlotRequest request) {
        return recommendationService.recommend(user.id(), request);
    }
}
