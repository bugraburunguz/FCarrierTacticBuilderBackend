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
    private final LevelReferenceService levelReferences;

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
        Map<String, FitPlayer> players = new HashMap<>();
        LevelReferenceService.LevelRefs level = levelRefs(userId, request, formation, players);
        Input input = new Input(formation.id(), BuildUpType.parse(request.getBuildUp()), Objects.isNull(request.getDepth()) ? DEFAULT_DEPTH : request.getDepth(), slots, players, level.refs(), level.label());
        Analysis analysis = analyzer.analyze(input);
        log.info("Tactic analysed formation : {} findings : {} players : {}", formation.id(), analysis.findings().size(), players.size());
        return analysis;
    }

    /**
     * Atanan oyuncuları çözer (players dolar) ve seviye referansını döner: kariyerde kullanıcının liginin aynı mevki medyanı (kariyer sahipliği doğrulanır), kariyer dışında (UT/katalog)
     * atanan oyuncuların ortalama OVR bandının katalog medyanı. Oyuncu atanmadıysa referans gerekmez.
     */
    private LevelReferenceService.LevelRefs levelRefs(Long userId, TacticAnalysisRequest request, FormationDefinition formation, Map<String, FitPlayer> players) {
        if (Objects.isNull(request.getAssigned()) || request.getAssigned().isEmpty()) {
            return LevelReferenceService.LevelRefs.NONE;
        }
        request.getAssigned().keySet().forEach((id) -> formation.slot(id).orElseThrow(() -> new InvalidTacticException("slot " + id)));
        boolean career = Objects.nonNull(request.getCareerId());
        CareerService.CareerSquad squad = career ? careerService.squadPlayerIds(userId, request.getCareerId()) : null;
        Short gameVersion = career ? squad.gameVersionId() : null;
        Map<Long, FitPlayer> byId = playerService.fitPlayers(List.copyOf(request.getAssigned().values()), gameVersion, career ? squad.careerId() : null).stream()
                .collect(Collectors.toMap(FitPlayer::getId, Function.identity(), (a, b) -> a));
        request.getAssigned().forEach((slot, id) -> {
            if (byId.containsKey(id)) {
                players.put(slot, byId.get(id));
            }
        });
        if (players.isEmpty()) {
            return LevelReferenceService.LevelRefs.NONE;
        }
        if (career) {
            return levelReferences.forCareer(squad.careerId(), squad.ownedIds());
        }
        double avgOvr = players.values().stream().filter((p) -> Objects.nonNull(p.getOverall())).mapToInt(FitPlayer::getOverall).average().orElse(0);
        return avgOvr > 0 ? levelReferences.forCatalog(playerService.resolveGameVersion(null), avgOvr) : LevelReferenceService.LevelRefs.NONE;
    }
}
