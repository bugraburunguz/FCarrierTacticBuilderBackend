package com.fcareer.tacticanalysis;

import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.IntentProfile;
import com.fcareer.fitengine.model.RuleSeverity;
import com.fcareer.fitengine.model.TeamSetup;
import com.fcareer.fitengine.model.response.RuleFinding;
import com.fcareer.fitengine.model.response.SlotAssignment;
import com.fcareer.tacticanalysis.TacticAnalysisModels.Analysis;
import com.fcareer.tacticanalysis.TacticAnalysisModels.Finding;
import com.fcareer.tacticanalysis.TacticAnalysisModels.Input;
import com.fcareer.tacticanalysis.TacticAnalysisModels.Slot;
import com.fcareer.tactics.model.FormationDefinition;
import com.fcareer.tactics.service.FormationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * `/fit/squad` yanıtındaki `rules` artık etiket tabanlı eski kural motorundan değil analiz motorundan gelir (E3-7): çözülmüş taktik (formasyon konumları, rol kimlikleri, hat, build-up)
 * ve çözüm sonucu atanan oyuncuların attribute'ları. Hareket profili olmayan bir rol varsa kurallar boş döner (sessizce, loglanır).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SquadFitRuleAdapter {

    private static final int DEFAULT_DEPTH = 50;

    private final FormationRegistry formations;
    private final RoleMovements movements;
    private final TacticAnalyzer analyzer;
    private final LevelReferenceService levelReferences;
    private final com.fcareer.catalog.service.PlayerService playerService;

    /** careerId/ownedIds: kariyer sorgusunda seviye referansı için (kullanıcının ligi); kariyer dışında OVR bandı katalog medyanı. */
    public List<RuleFinding> findings(String formationId, TeamSetup setup, List<IntentProfile> intents, List<SlotAssignment> assigned, List<FitPlayer> squad, Long careerId, java.util.Collection<Long> ownedIds) {
        FormationDefinition formation = formations.get(formationId);
        List<Slot> slots = new ArrayList<>();
        for (IntentProfile intent : intents) {
            String roleId = intent.getResolvedRole().getId();
            var slot = formation.slot(intent.getSlotId());
            if (slot.isEmpty() || movements.find(roleId).isEmpty()) {
                log.warn("Analysis skipped formation : {} slot : {} role : {}", formationId, intent.getSlotId(), roleId);
                return List.of();
            }
            slots.add(new Slot(slot.get().slotId(), slot.get().position(), slot.get().x(), slot.get().y(), roleId));
        }
        Map<Long, FitPlayer> byId = squad.stream().collect(Collectors.toMap(FitPlayer::getId, Function.identity(), (a, b) -> a));
        Map<String, FitPlayer> players = new HashMap<>();
        for (SlotAssignment a : assigned) {
            if (Objects.nonNull(a.getPlayerId()) && byId.containsKey(a.getPlayerId())) {
                players.put(a.getSlotId(), byId.get(a.getPlayerId()));
            }
        }
        int depth = Objects.isNull(setup) || Objects.isNull(setup.getDepth()) ? DEFAULT_DEPTH : setup.getDepth();
        LevelReferenceService.LevelRefs level = players.isEmpty() ? LevelReferenceService.LevelRefs.NONE : Objects.nonNull(careerId) ? levelReferences.forCareer(careerId, ownedIds)
                : levelReferences.forCatalog(playerService.resolveGameVersion(null), players.values().stream().filter((p) -> Objects.nonNull(p.getOverall())).mapToInt(FitPlayer::getOverall).average().orElse(60));
        Analysis analysis = analyzer.analyze(new Input(formation.id(), BuildUpType.parse(Objects.isNull(setup) ? null : setup.getBuildUp()), depth, slots, players, level.refs(), level.label()));
        return analysis.findings().stream().map(SquadFitRuleAdapter::toRule).toList();
    }

    private static RuleFinding toRule(Finding f) {
        String suffix = f.instance().isEmpty() ? "" : "_" + String.join("_", f.instance().entrySet().stream().filter((e) -> "side".equals(e.getKey())).map(Map.Entry::getValue).toList());
        List<Map<String, Object>> evidence = f.evidence().stream().map((e) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("metric", e.metric());
            m.put("params", e.params());
            m.put("value", e.value());
            m.put("op", e.op());
            m.put("threshold", e.threshold());
            return m;
        }).collect(Collectors.toList());
        return RuleFinding.builder().ruleId(f.code() + suffix).severity(RuleSeverity.valueOf(f.severity().name())).title(f.title()).message(f.text()).fix(f.recommendation().text()).evidence(evidence).build();
    }
}
