package com.fcareer.replica.controller;

import com.fcareer.auth.security.AuthUser;
import com.fcareer.replica.ReplicaApplyService;
import com.fcareer.replica.model.ReplicaApplyRequest;
import com.fcareer.replica.model.ReplicaApplyResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.fcareer.common.constants.ApiConstants.API_V1;

/** "Kadromla uygula" (REP-07): kredi harcamaz; atama + slot başına seviye rozeti + oyunculu analiz. */
@RestController
@RequestMapping(API_V1 + "/replicas/{id}/apply")
@RequiredArgsConstructor
public class ReplicaApplyController {

    private final ReplicaApplyService service;

    @PostMapping
    public ReplicaApplyResponse apply(@AuthenticationPrincipal AuthUser user, @PathVariable String id, @Valid @RequestBody ReplicaApplyRequest request) {
        return service.apply(user.id(), id, request);
    }
}
