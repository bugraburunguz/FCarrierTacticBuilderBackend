package com.fcareer.tactics.controller;

import com.fcareer.tactics.model.FormationDefinition;
import com.fcareer.tactics.model.request.TacticRequest;
import com.fcareer.tactics.model.response.PresetResponse;
import com.fcareer.tactics.model.response.ResolvedTacticResponse;
import com.fcareer.tactics.service.TacticService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.fcareer.common.constants.ApiConstants.API_V1;

@RestController
@RequestMapping(API_V1)
@RequiredArgsConstructor
public class TacticController {

    private final TacticService tacticService;

    @GetMapping("/formations")
    public List<FormationDefinition> formations() {
        return tacticService.formations();
    }

    @GetMapping("/presets")
    public List<PresetResponse> presets(@RequestParam(required = false) String kind) {
        return tacticService.presets(kind);
    }

    @GetMapping("/presets/{id}")
    public PresetResponse preset(@PathVariable String id) {
        return tacticService.preset(id);
    }

    @PostMapping("/tactics/resolve")
    public ResolvedTacticResponse resolve(@Valid @RequestBody TacticRequest request) {
        return tacticService.resolve(request);
    }
}
