package com.fcareer.manager;

import com.fcareer.auth.security.AuthUser;
import com.fcareer.manager.ManagerModels.ManagerProfile;
import com.fcareer.manager.ManagerModels.ProfileView;
import com.fcareer.manager.ManagerModels.Recommendations;
import com.fcareer.manager.ManagerModels.RecommendationRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static com.fcareer.common.constants.ApiConstants.API_V1;

/** MGR-a: menajer profili (hesaba bağlı, tek) ve ücretsiz kulüp önerisi (analiz kovası). */
@RestController
@RequiredArgsConstructor
public class ManagerController {

    private final ManagerService service;

    /** Kayıtlı profil; yoksa data null (200). */
    @GetMapping(API_V1 + "/me/manager-profile")
    public ProfileView profile(@AuthenticationPrincipal AuthUser user) {
        return service.profile(user.id());
    }

    @PutMapping(API_V1 + "/me/manager-profile")
    public ProfileView save(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody ManagerProfile profile) {
        return service.save(user.id(), profile);
    }

    @PostMapping(API_V1 + "/manager/club-recommendations")
    public Recommendations recommend(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody(required = false) RecommendationRequest request) {
        return service.recommend(user.id(), request);
    }
}
