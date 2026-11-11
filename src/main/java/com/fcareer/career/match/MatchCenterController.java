package com.fcareer.career.match;

import com.fcareer.auth.security.AuthUser;
import com.fcareer.career.match.MatchCenterModels.Opponent;
import com.fcareer.career.match.MatchCenterModels.OpponentReport;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.fcareer.common.constants.ApiConstants.API_V1;

@RestController
@RequestMapping(API_V1 + "/careers/{careerId}/match")
@RequiredArgsConstructor
public class MatchCenterController {

    private final MatchCenterService service;

    @GetMapping("/opponents")
    public List<Opponent> opponents(@AuthenticationPrincipal AuthUser user, @PathVariable Long careerId) {
        return service.listOpponents(user.id(), careerId);
    }

    @GetMapping("/opponents/{teamId}/report")
    public OpponentReport report(@AuthenticationPrincipal AuthUser user, @PathVariable Long careerId, @PathVariable Integer teamId) {
        return service.buildReport(user.id(), careerId, teamId);
    }
}
