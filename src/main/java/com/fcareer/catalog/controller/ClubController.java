package com.fcareer.catalog.controller;

import com.fcareer.catalog.model.response.ClubResponse;
import com.fcareer.catalog.model.response.LookupItem;
import com.fcareer.catalog.service.ClubService;
import com.fcareer.catalog.service.LookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.fcareer.common.constants.ApiConstants.API_V1;

@RestController
@RequestMapping(API_V1)
@RequiredArgsConstructor
public class ClubController {

    private final ClubService clubService;
    private final LookupService lookupService;

    @GetMapping("/clubs")
    public List<ClubResponse> clubs(@RequestParam(name = "q", required = false) String query,
                                    @RequestParam(name = "limit", required = false) Integer limit,
                                    @RequestParam(name = "league", required = false) Integer leagueId,
                                    @RequestParam(name = "gender", required = false) Integer gender) {
        return clubService.search(query, limit, leagueId, gender);
    }

    @GetMapping("/leagues")
    public List<LookupItem> leagues(@RequestParam(name = "gender", required = false) Integer gender) {
        return lookupService.leagues(gender);
    }

    @GetMapping("/nationalities")
    public List<LookupItem> nationalities() {
        return lookupService.nationalities();
    }
}
