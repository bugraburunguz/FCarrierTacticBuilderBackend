package com.fcareer.replica;

import com.fcareer.career.service.CareerService;
import com.fcareer.catalog.model.request.PlayerSearchRequest;
import com.fcareer.catalog.model.response.PlayerSummaryResponse;
import com.fcareer.catalog.service.PlayerService;
import com.fcareer.common.exception.InvalidTacticException;
import com.fcareer.common.exception.NotFoundException;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.IntentProfile;
import com.fcareer.fitengine.model.response.RoleFitResponse;
import com.fcareer.fitengine.model.response.SlotAssignment;
import com.fcareer.fitengine.model.response.SquadFitResponse;
import com.fcareer.fitengine.service.FitEngineV3;
import com.fcareer.fitengine.service.SquadFitService;
import com.fcareer.replica.ReplicaService.Replica;
import com.fcareer.replica.ReplicaService.RoleSlot;
import com.fcareer.replica.model.ReplicaApplyRequest;
import com.fcareer.replica.model.ReplicaApplyResponse;
import com.fcareer.tacticanalysis.TacticAnalysisModels.Analysis;
import com.fcareer.tacticanalysis.TacticAnalysisService;
import com.fcareer.tacticanalysis.model.TacticAnalysisRequest;
import com.fcareer.tactics.model.FormationDefinition;
import com.fcareer.tactics.model.FormationSlot;
import com.fcareer.tactics.service.FormationRegistry;
import com.fcareer.tactics.service.TacticResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * REP-07 "Kadromla uygula": replikanın 11 rolüne oyuncu atar. Atama Hungarian çözücüyle ve v3 uyumu (currentFit) üzerinden yapılır; ayna çözüm denenmez (replika tarafları bilinçlidir).
 * Kaynak kariyer kadrosu, UT kulüp kartları (katalog kimlikleri) ya da hiçbiri (katalogdan mevki başına en iyi aday: "örnek"). Boş ya da zayıf slotlar "öner" bağlantısı alır.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplicaApplyService {

    public static final String SOURCE_CAREER = "CAREER";
    public static final String SOURCE_UT = "UT";
    public static final String SOURCE_SAMPLE = "SAMPLE";
    private static final String EMPTY = "EMPTY";
    private static final String SORT_OVERALL = "overall";
    private static final int SAMPLE_PER_POSITION = 6;
    private static final Set<String> SUGGEST_STATES = Set.of(EMPTY, "WEAK", "WRONG_ROLE");

    private final ReplicaService replicas;
    private final FormationRegistry formations;
    private final TacticResolver tacticResolver;
    private final SquadFitService squadFitService;
    private final PlayerService playerService;
    private final CareerService careerService;
    private final TacticAnalysisService analysisService;
    private final com.fcareer.career.desk.SpoilerPolicy spoilerPolicy;

    public ReplicaApplyResponse apply(Long userId, String replicaId, ReplicaApplyRequest request) {
        Replica replica = replicas.find(replicaId).orElseThrow(() -> new NotFoundException("replica", replicaId));
        boolean career = Objects.nonNull(request.getCareerId());
        boolean ut = Objects.nonNull(request.getPlayerIds()) && !request.getPlayerIds().isEmpty();
        if (career && ut) {
            throw new InvalidTacticException("squad source (careerId or playerIds)");
        }
        FormationDefinition formation = formations.get(replica.formationId());
        Map<String, RoleSlot> roleBySlot = new HashMap<>();
        replica.slots().forEach((s) -> roleBySlot.put(s.slotId(), s));

        String source;
        List<FitPlayer> players;
        Short gameVersion = null;
        if (career) {
            CareerService.CareerSquad squad = careerService.squadPlayerIds(userId, request.getCareerId());
            source = SOURCE_CAREER;
            gameVersion = squad.gameVersionId();
            players = playerService.fitPlayers(squad.playerIds(), gameVersion, request.getCareerId());
        } else if (ut) {
            source = SOURCE_UT;
            players = playerService.fitPlayers(request.getPlayerIds(), null, null);
        } else {
            source = SOURCE_SAMPLE;
            players = playerService.fitPlayers(sampleIds(formation), null, null);
        }

        List<IntentProfile> intents = new ArrayList<>();
        for (FormationSlot slot : formation.slots()) {
            intents.add(tacticResolver.slotIntent(slot.slotId(), slot.position(), null, roleBySlot.get(slot.slotId()).roleId(), List.of()));
        }
        SquadFitResponse solved = squadFitService.solve(players, intents, FitEngineV3.VERSION, true);

        Map<Long, PlayerSummaryResponse> summaries = playerService.summaries(solved.getSlots().stream().map(SlotAssignment::getPlayerId).filter(Objects::nonNull).toList(), gameVersion, request.getCareerId());
        // REVIEW F2: kariyer kadrosuyla uygulamada spoiler politikası (SCOUT_RANGE: kesin POT yerine bant), diğer uçlarla aynı.
        if (career) {
            spoilerPolicy.maskAll(request.getCareerId(), summaries.values());
        }
        List<ReplicaApplyResponse.Slot> slots = new ArrayList<>();
        Map<String, Long> assigned = new LinkedHashMap<>();
        for (SlotAssignment a : solved.getSlots()) {
            RoleSlot role = roleBySlot.get(a.getSlotId());
            RoleFitResponse fit = a.getRoleFit();
            boolean filled = Objects.nonNull(a.getPlayerId());
            String state = !filled || Objects.isNull(fit) || Objects.isNull(fit.getFitLabel()) ? EMPTY : fit.getFitLabel();
            boolean outOfPosition = filled && Objects.nonNull(fit) && fit.isOutOfPosition();
            if (filled) {
                assigned.put(a.getSlotId(), a.getPlayerId());
            }
            slots.add(new ReplicaApplyResponse.Slot(a.getSlotId(), a.getPosition(), role.roleId(), role.role(), role.focus(), filled ? summaries.get(a.getPlayerId()) : null,
                    filled && Objects.nonNull(fit) ? fit.getCurrentFit() : null, filled && Objects.nonNull(fit) ? fit.getLevelFit() : null, state, filled && Objects.nonNull(fit) ? fit.getBadgeText() : null,
                    SUGGEST_STATES.contains(state), outOfPosition, outOfPosition ? summaries.get(a.getPlayerId()).getPositions().get(0) : null));
        }
        Analysis analysis = analysisService.analyze(userId, analysisRequest(replica, formation, roleBySlot, career ? request.getCareerId() : null, assigned));
        log.info("Replica applied id : {} source : {} players : {} filled : {}", replicaId, source, players.size(), assigned.size());
        return new ReplicaApplyResponse(replicaId, source, solved.getSquadFit(), slots, analysis);
    }

    private static TacticAnalysisRequest analysisRequest(Replica replica, FormationDefinition formation, Map<String, RoleSlot> roleBySlot, Long careerId, Map<String, Long> assigned) {
        TacticAnalysisRequest req = new TacticAnalysisRequest();
        req.setFormation(formation.id());
        req.setBuildUp(replica.buildUp());
        req.setDepth(replica.lineHeight());
        req.setCareerId(careerId);
        req.setAssigned(assigned);
        List<TacticAnalysisRequest.SlotRole> slotRoles = new ArrayList<>();
        formation.slots().forEach((s) -> {
            TacticAnalysisRequest.SlotRole sr = new TacticAnalysisRequest.SlotRole();
            sr.setSlotId(s.slotId());
            sr.setRoleId(roleBySlot.get(s.slotId()).roleId());
            slotRoles.add(sr);
        });
        req.setSlots(slotRoles);
        return req;
    }

    /** Örnek kadro: formasyondaki her mevki için katalogdaki en yüksek OVR'li doğal adaylar. */
    private List<Long> sampleIds(FormationDefinition formation) {
        Set<Long> ids = new LinkedHashSet<>();
        for (String position : formation.slots().stream().map(FormationSlot::position).distinct().toList()) {
            PlayerSearchRequest search = PlayerSearchRequest.builder().positions(List.of(position)).sort(SORT_OVERALL).size(SAMPLE_PER_POSITION).build();
            playerService.search(search).getItems().forEach((p) -> ids.add(p.getId()));
        }
        return List.copyOf(ids);
    }
}
