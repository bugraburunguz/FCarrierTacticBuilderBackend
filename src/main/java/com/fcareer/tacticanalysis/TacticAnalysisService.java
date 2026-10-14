package com.fcareer.tacticanalysis;

import com.fcareer.career.service.CareerService;
import com.fcareer.catalog.service.PlayerService;
import com.fcareer.common.exception.InvalidTacticException;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.tacticanalysis.TacticAnalysisModels.Analysis;
import com.fcareer.tacticanalysis.TacticAnalysisModels.Input;
import com.fcareer.tacticanalysis.TacticAnalysisModels.Slot;
import com.fcareer.tacticanalysis.model.TacticAnalysisRequest;
import com.fcareer.tactics.model.FormationDefinition;
import com.fcareer.tactics.model.FormationSlot;
import com.fcareer.tactics.service.FormationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Taktik analizi uç servisi (13 §3.4): istek → saf analiz girdisi (formasyon konumları, rol kimlikleri, varsa kariyer oyuncuları). */
@Slf4j
@Service
@RequiredArgsConstructor
public class TacticAnalysisService {

    private static final int DEFAULT_DEPTH = 50;

    private final FormationRegistry formations;
    private final RoleMovements movements;
    private final TacticAnalyzer analyzer;
    private final CareerService careerService;
    private final PlayerService playerService;

    public Analysis analyze(Long userId, TacticAnalysisRequest request) {
        FormationDefinition formation = formations.get(request.getFormation());
        Map<String, String> chosen = new HashMap<>();
        request.getSlots().forEach((s) -> chosen.put(s.getSlotId(), s.getRoleId()));
        chosen.keySet().forEach((id) -> formation.slot(id).orElseThrow(() -> new InvalidTacticException("slot " + id)));
        List<Slot> slots = new ArrayList<>();
        for (FormationSlot s : formation.slots()) {
            String roleId = chosen.getOrDefault(s.slotId(), s.defaultRole());
            if (movements.find(roleId).isEmpty()) {
                throw new InvalidTacticException("role " + roleId);
            }
            slots.add(new Slot(s.slotId(), s.position(), s.x(), s.y(), roleId));
        }
        Map<String, FitPlayer> players = players(userId, request, formation);
        Input input = new Input(formation.id(), BuildUpType.parse(request.getBuildUp()), Objects.isNull(request.getDepth()) ? DEFAULT_DEPTH : request.getDepth(), slots, players);
        Analysis analysis = analyzer.analyze(input);
        log.info("Tactic analysed formation : {} findings : {} players : {}", formation.id(), analysis.findings().size(), players.size());
        return analysis;
    }

    private Map<String, FitPlayer> players(Long userId, TacticAnalysisRequest request, FormationDefinition formation) {
        if (Objects.isNull(request.getCareerId()) || Objects.isNull(request.getAssigned()) || request.getAssigned().isEmpty()) {
            return Map.of();
        }
        CareerService.CareerSquad squad = careerService.squadPlayerIds(userId, request.getCareerId());
        request.getAssigned().keySet().forEach((id) -> formation.slot(id).orElseThrow(() -> new InvalidTacticException("slot " + id)));
        Map<Long, FitPlayer> byId = playerService.fitPlayers(List.copyOf(request.getAssigned().values()), squad.gameVersionId(), squad.careerId()).stream()
                .collect(Collectors.toMap(FitPlayer::getId, Function.identity(), (a, b) -> a));
        Map<String, FitPlayer> out = new HashMap<>();
        request.getAssigned().forEach((slot, id) -> {
            if (byId.containsKey(id)) {
                out.put(slot, byId.get(id));
            }
        });
        return out;
    }
}
