package com.fcareer.development.track;

import com.fcareer.auth.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.fcareer.common.constants.ApiConstants.API_V1;

/** GTR-b: Gelişim satırı "Son {N} ayda: +3 OVR · 18 maç" (kadro oyuncuları; önceki görüntü yoksa boş liste). */
@RestController
@RequiredArgsConstructor
@RequestMapping(API_V1 + "/careers/{careerId}")
public class GrowthRecentController {

    private final GrowthRecentService service;

    @GetMapping("/growth-recent")
    public List<GrowthRecentService.Row> recent(@AuthenticationPrincipal AuthUser user, @PathVariable Long careerId) {
        return service.recent(user.id(), careerId);
    }
}
