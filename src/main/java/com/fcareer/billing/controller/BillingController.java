package com.fcareer.billing.controller;

import com.fcareer.auth.security.AuthUser;
import com.fcareer.billing.configuration.BillingProperties;
import com.fcareer.billing.model.response.CreditCostsResponse;
import com.fcareer.billing.model.response.SubscriptionResponse;
import com.fcareer.billing.service.BillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.fcareer.common.constants.ApiConstants.API_V1;

@RestController
@RequestMapping(API_V1 + "/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;
    private final BillingProperties billingProperties;

    @GetMapping("/costs")
    public CreditCostsResponse costs() {
        return new CreditCostsResponse(billingProperties.getRecommendCost(), billingProperties.getSquadFitCost(), billingProperties.getAdvisorCost());
    }

    @PostMapping("/subscribe")
    public SubscriptionResponse subscribe(@AuthenticationPrincipal AuthUser user) {
        return billingService.subscribe(user.id());
    }
}
