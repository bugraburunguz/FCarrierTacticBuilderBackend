package com.fcareer.career.match;

import com.fcareer.auth.security.AuthUser;
import com.fcareer.career.match.MatchModels.CompetitionSummary;
import com.fcareer.career.match.MatchModels.Leader;
import com.fcareer.career.match.MatchModels.PlayerMatches;
import com.fcareer.career.match.MatchModels.SquadForm;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.fcareer.common.constants.ApiConstants.API_V1;

@RestController
@RequestMapping(API_V1 + "/careers/{careerId}")
@RequiredArgsConstructor
public class MatchStatsController {

    private final MatchStatsService service;

    @GetMapping("/players/{playerId}/matches")
    public PlayerMatches player(@AuthenticationPrincipal AuthUser user, @PathVariable Long careerId, @PathVariable Long playerId) {
        return service.player(user.id(), careerId, playerId);
    }

    @GetMapping("/squad/form")
    public List<SquadForm> squadForm(@AuthenticationPrincipal AuthUser user, @PathVariable Long careerId) {
        return service.squadForm(user.id(), careerId);
    }

    @GetMapping("/stats/competitions")
    public List<CompetitionSummary> competitions(@AuthenticationPrincipal AuthUser user, @PathVariable Long careerId) {
        return service.competitions(user.id(), careerId);
    }

    @GetMapping("/stats/leaders")
    public List<Leader> leaders(@AuthenticationPrincipal AuthUser user, @PathVariable Long careerId,
                                @RequestParam(name = "competition", required = false) Integer competitionId,
                                @RequestParam(name = "metric", required = false) String metric,
                                @RequestParam(name = "limit", required = false) Integer limit) {
        return service.leaders(user.id(), careerId, competitionId, metric, limit);
    }
}
