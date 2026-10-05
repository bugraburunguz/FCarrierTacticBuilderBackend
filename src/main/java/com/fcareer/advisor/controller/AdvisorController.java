package com.fcareer.advisor.controller;

import com.fcareer.advisor.model.request.AdvisorRequest;
import com.fcareer.advisor.model.response.AdvisorResponses.Advice;
import com.fcareer.advisor.service.AdvisorService;
import com.fcareer.auth.security.AuthUser;
import com.fcareer.billing.model.response.CreditedResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.fcareer.common.constants.ApiConstants.API_V1;

@RestController
@RequestMapping(API_V1 + "/advisor")
@RequiredArgsConstructor
public class AdvisorController {

    private final AdvisorService advisorService;

    @PostMapping("/squad")
    public CreditedResponse<Advice> advise(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody AdvisorRequest request) {
        return advisorService.advise(user.id(), request);
    }
}
