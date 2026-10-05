package com.fcareer.billing.controller;

import com.fcareer.auth.security.AuthUser;
import com.fcareer.billing.model.response.SubscriptionResponse;
import com.fcareer.billing.service.BillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.fcareer.common.constants.ApiConstants.API_V1;

@RestController
@RequestMapping(API_V1 + "/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;

    @PostMapping("/subscribe")
    public SubscriptionResponse subscribe(@AuthenticationPrincipal AuthUser user) {
        return billingService.subscribe(user.id());
    }
}
