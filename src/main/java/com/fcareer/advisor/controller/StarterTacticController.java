package com.fcareer.advisor.controller;

import com.fcareer.advisor.model.response.StarterTactic;
import com.fcareer.advisor.service.StarterTacticService;
import com.fcareer.auth.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static com.fcareer.common.constants.ApiConstants.API_V1;

/** Ücretsiz otomatik başlangıç önerisi (D-31): ana taktik yokken. Kredi düşmez; sahiplik doğrulanır. */
@RestController
@RequiredArgsConstructor
public class StarterTacticController {

    private final StarterTacticService service;

    @GetMapping(API_V1 + "/careers/{careerId}/starter-tactic")
    public StarterTactic starter(@AuthenticationPrincipal AuthUser user, @PathVariable Long careerId) {
        return service.starter(user.id(), careerId);
    }
}
