package com.fcareer.career.academy;

import com.fcareer.auth.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    /** ACADEMY-b: Gelişim planları satırları (kadro gençleri, kiralıktakiler dahil). */
    @GetMapping("/youth")
    public List<AcademyService.AcademyPlayer> youth(@AuthenticationPrincipal AuthUser user, @PathVariable Long careerId) {
        return service.youth(user.id(), careerId);
    }

    /** ACADEMY-b: oyuncu paneli (altyapı ya da kadro genci). */
    @GetMapping("/youth/{playerId}")
    public AcademyService.YouthDetail detail(@AuthenticationPrincipal AuthUser user, @PathVariable Long careerId, @PathVariable Long playerId) {
        return service.detail(user.id(), careerId, playerId);
    }
}
