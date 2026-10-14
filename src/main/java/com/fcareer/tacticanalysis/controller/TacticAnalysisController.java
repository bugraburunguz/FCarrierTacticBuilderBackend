package com.fcareer.tacticanalysis.controller;

import com.fcareer.auth.security.AuthUser;
import com.fcareer.tacticanalysis.TacticAnalysisModels.Analysis;
import com.fcareer.tacticanalysis.TacticAnalysisService;
import com.fcareer.tacticanalysis.model.TacticAnalysisRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.fcareer.common.constants.ApiConstants.API_V1;

/** Taktik analizi (E3-7): 7 metrik + kural bulguları. Hesap saf ve ucuzdur; kredi harcamaz. */
@RestController
@RequestMapping(API_V1 + "/tactics/analysis")
@RequiredArgsConstructor
public class TacticAnalysisController {

    private final TacticAnalysisService service;

    @PostMapping
    public Analysis analyze(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody TacticAnalysisRequest request) {
        return service.analyze(user.id(), request);
    }
}
