package com.fcareer.ut.controller;

import com.fcareer.ut.model.SbcModels.ScoreTable;
import com.fcareer.ut.model.SbcModels.StreamlinedRequest;
import com.fcareer.ut.model.SbcModels.StreamlinedResult;
import com.fcareer.ut.service.SbcSolverService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.fcareer.common.constants.ApiConstants.API_V1;

@RestController
@RequestMapping(API_V1 + "/ut/sbc")
@RequiredArgsConstructor
public class UtSbcController {

    private final SbcSolverService sbcSolverService;

    @GetMapping("/score-table")
    public ScoreTable scoreTable() {
        return sbcSolverService.scoreTable();
    }

    @PostMapping("/streamlined")
    public StreamlinedResult streamlined(@Valid @RequestBody StreamlinedRequest request) {
        return sbcSolverService.solveStreamlined(request);
    }
}
