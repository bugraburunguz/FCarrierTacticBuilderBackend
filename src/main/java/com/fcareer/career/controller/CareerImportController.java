package com.fcareer.career.controller;

import com.fcareer.auth.security.AuthUser;
import com.fcareer.career.importer.CareerImportService;
import com.fcareer.career.importer.TeamInsightService;
import com.fcareer.career.model.response.ImportSummaryResponse;
import com.fcareer.career.model.response.TeamProfileResponse;
import com.fcareer.career.model.response.TeamSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

import static com.fcareer.common.constants.ApiConstants.API_V1;

@RestController
@RequestMapping(API_V1 + "/careers/{id}")
@RequiredArgsConstructor
public class CareerImportController {

    private static final int MAX_TEAMS = 100;

    private final CareerImportService careerImportService;
    private final TeamInsightService teamInsightService;
    private final com.fcareer.career.desk.SpoilerPolicy spoilerPolicy;

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportSummaryResponse importData(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                            @RequestParam(value = "log", required = false) MultipartFile log,
                                            @RequestParam(value = "players", required = false) MultipartFile players,
                                            @RequestParam(value = "teams", required = false) MultipartFile teams,
                                            @RequestParam(value = "teamplayerlinks", required = false) MultipartFile links,
                                            @RequestParam(value = "leagueteamlinks", required = false) MultipartFile leagueLinks,
                                            @RequestParam(value = "leagues", required = false) MultipartFile leagues) {
        return careerImportService.importData(user.id(), id, log, players, teams, links, leagueLinks, leagues);
    }

    @PostMapping("/import/squad")
    public Map<String, Integer> syncSquad(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                          @RequestParam("teamId") Integer teamId) {
        return Map.of("players", careerImportService.syncSquad(user.id(), id, teamId));
    }

    @GetMapping("/teams")
    public List<TeamSummaryResponse> teams(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                           @RequestParam(value = "q", required = false) String query,
                                           @RequestParam(value = "limit", defaultValue = "30") int limit) {
        return teamInsightService.teams(user.id(), id, query, Math.min(Math.max(limit, 1), MAX_TEAMS));
    }

    @GetMapping("/teams/{teamId}")
    public TeamProfileResponse team(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @PathVariable Integer teamId) {
        TeamProfileResponse profile = teamInsightService.profile(user.id(), id, teamId);
        spoilerPolicy.maskAll(id, profile.getTopPlayers());
        return profile;
    }
}
