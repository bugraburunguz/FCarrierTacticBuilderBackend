package com.fcareer.career.academy;

import com.fcareer.auth.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.fcareer.common.constants.ApiConstants.API_V1;

@RestController
@RequestMapping(API_V1 + "/careers/{careerId}")
@RequiredArgsConstructor
public class AcademyController {

    private final AcademyService service;

    @GetMapping("/academy")
    public AcademyService.Academy academy(@AuthenticationPrincipal AuthUser user, @PathVariable Long careerId) {
        return service.academy(user.id(), careerId);
    }
}
