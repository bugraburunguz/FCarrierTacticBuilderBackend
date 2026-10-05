package com.fcareer.tactics.service;

import com.fcareer.tactics.mapper.TacticMapper;
import com.fcareer.tactics.model.FormationDefinition;
import com.fcareer.tactics.model.request.TacticRequest;
import com.fcareer.tactics.model.response.PresetResponse;
import com.fcareer.tactics.model.response.ResolvedTacticResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TacticService {

    private final FormationRegistry formationRegistry;
    private final PresetService presetService;
    private final TacticResolver tacticResolver;
    private final TacticMapper tacticMapper;

    public List<FormationDefinition> formations() {
        return formationRegistry.findAll();
    }

    public List<PresetResponse> presets(String kind) {
        return presetService.findByKind(kind);
    }

    public PresetResponse preset(String presetId) {
        return presetService.get(presetId);
    }

    public ResolvedTacticResponse resolve(TacticRequest request) {
        return tacticMapper.toResolvedResponse(tacticResolver.resolve(request));
    }
}
