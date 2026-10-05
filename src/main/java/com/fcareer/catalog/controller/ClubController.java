package com.fcareer.catalog.controller;

import com.fcareer.catalog.model.response.ClubResponse;
import com.fcareer.catalog.service.ClubService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.fcareer.common.constants.ApiConstants.API_V1;

@RestController
@RequestMapping(API_V1 + "/clubs")
@RequiredArgsConstructor
public class ClubController {

    private final ClubService clubService;

    @GetMapping
    public List<ClubResponse> search(@RequestParam(name = "q", required = false) String query,
                                     @RequestParam(name = "limit", required = false) Integer limit) {
        return clubService.search(query, limit);
    }
}
