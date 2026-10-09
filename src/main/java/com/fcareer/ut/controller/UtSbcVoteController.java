package com.fcareer.ut.controller;

import com.fcareer.auth.security.AuthUser;
import com.fcareer.ut.model.SbcVoteModels.SetVotes;
import com.fcareer.ut.model.SbcVoteModels.VoteRequest;
import com.fcareer.ut.model.SbcVoteModels.VoteSummary;
import com.fcareer.ut.service.SbcVoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.fcareer.common.constants.ApiConstants.API_V1;

@RestController
@RequestMapping(API_V1 + "/ut/sbc/votes")
@RequiredArgsConstructor
public class UtSbcVoteController {

    private final SbcVoteService sbcVoteService;

    @GetMapping
    public VoteSummary summary(@AuthenticationPrincipal AuthUser user) {
        return sbcVoteService.summary(user.id());
    }

    @PutMapping("/{setId}")
    public SetVotes vote(@AuthenticationPrincipal AuthUser user, @PathVariable Long setId, @Valid @RequestBody VoteRequest request) {
        return sbcVoteService.vote(user.id(), setId, request.vote());
    }
}
