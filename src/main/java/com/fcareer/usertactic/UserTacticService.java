package com.fcareer.usertactic;

import com.fcareer.career.service.CareerService;
import com.fcareer.common.exception.FcareerRuntimeException;
import com.fcareer.common.exception.InvalidTacticException;
import com.fcareer.common.exception.NotFoundException;
import com.fcareer.fitengine.model.TeamSetup;
import com.fcareer.replica.ReplicaService;
import com.fcareer.replica.ReplicaService.Replica;
import com.fcareer.tacticcode.service.TacticCodeService;
import com.fcareer.tactics.model.FormationDefinition;
import com.fcareer.tactics.model.FormationSlot;
import com.fcareer.tactics.model.request.SlotSelection;
import com.fcareer.tactics.model.request.TacticRequest;
import com.fcareer.tactics.service.FormationRegistry;
import com.fcareer.usertactic.model.UserTacticModels.SaveRequest;
import com.fcareer.usertactic.model.UserTacticModels.UserTactic;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Kullanıcı taktik slotları (REP-07, KARIYER-UX D-30). Kayıt ya replikadan (kod ve ayarlar kopyalanır) ya Lab taslağından (tek taktik gövdesi TacticRequest +
 * opsiyonel diziliş; EA kodu replikadaki gibi codec'le üretilir). Kariyer başına bir ana taktik; /main yalnız kariyer içindir.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserTacticService {

    private static final int MIN_SLOT = 1;
    private static final int MAX_SLOT = 5;
    private static final String DRAFT_NAME = "Taslak";
    private static final String USER_TACTIC = "user tactic";

    private final UserTacticRepository repository;
    private final ReplicaService replicas;
    private final CareerService careerService;
    private final FormationRegistry formationRegistry;
    private final TacticCodeService tacticCodeService;

    public List<UserTactic> list(Long userId, Long careerId) {
        requireCareer(userId, careerId);
        return repository.list(userId, careerId);
    }

    @Transactional
    public UserTactic save(Long userId, int slot, SaveRequest request) {
        requireSlot(slot);
        requireCareer(userId, request.getCareerId());
        boolean fromReplica = Objects.nonNull(request.getReplicaId()) && !request.getReplicaId().isBlank();
        boolean fromDraft = Objects.nonNull(request.getTactic());
        if (fromReplica == fromDraft) {
            throw new InvalidTacticException("exactly one of replicaId or tactic");
        }
        UserTactic tactic = fromReplica ? fromReplica(slot, request) : fromDraft(slot, request);
        repository.upsert(userId, tactic);
        log.info("User tactic saved slot : {} replica : {} career : {}", slot, tactic.sourceReplicaId(), request.getCareerId());
        return repository.find(userId, request.getCareerId(), slot).orElse(tactic);
    }

    /** Ana taktik (yalnız kariyer). Yoksa null. Diziliş okuma anında kadroya göre süzülür: kadroda olmayan ya da bizden kiralıkta olan düşer (Q2). */
    public UserTactic main(Long userId, Long careerId) {
        long career = requireCareerId(userId, careerId);
        return repository.findMain(userId, career).map(t -> withAvailableLineup(userId, career, t)).orElse(null);
    }

    @Transactional
    public UserTactic setMain(Long userId, Long careerId, int slot) {
        requireSlot(slot);
        long career = requireCareerId(userId, careerId);
        if (repository.setMain(userId, career, slot) == 0) {
            throw new NotFoundException(USER_TACTIC, slot);
        }
        log.info("Main tactic set career : {} slot : {}", career, slot);
        return repository.find(userId, career, slot).orElseThrow(() -> new NotFoundException(USER_TACTIC, slot));
    }

    @Transactional
    public void delete(Long userId, Long careerId, int slot) {
        requireSlot(slot);
        requireCareer(userId, careerId);
        repository.delete(userId, careerId, slot);
    }

    private UserTactic fromReplica(int slot, SaveRequest request) {
        Replica replica = replicas.find(request.getReplicaId()).orElseThrow(() -> new NotFoundException("replica", request.getReplicaId()));
        List<Map<String, Object>> slots = new ArrayList<>();
        replica.slots().forEach((s) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("slot", s.slotId());
            row.put("position", s.position());
            row.put("roleId", s.roleId());
            row.put("focus", s.focus());
            row.put("tags", List.of());
            slots.add(row);
        });
        return new UserTactic(slot, request.getCareerId(), replica.coach() + " — " + replica.club() + " " + replica.season(), replica.formationId(), replica.buildUp(),
                replica.defensiveApproach(), replica.lineHeight(), slots, replica.tacticCode(), replica.id(), false, null, null, null, null, null);
    }

    private UserTactic fromDraft(int slot, SaveRequest request) {
        TacticRequest tactic = request.getTactic();
        FormationDefinition formation = formationRegistry.get(tactic.getFormation());
        Map<String, String> positionBySlot = formation.slots().stream().collect(Collectors.toMap(FormationSlot::slotId, FormationSlot::position));
        List<Map<String, Object>> slots = new ArrayList<>();
        for (SlotSelection selection : tactic.getSlots()) {
            if (!positionBySlot.containsKey(selection.getSlotId())) {
                throw new InvalidTacticException("unknown slot " + selection.getSlotId());
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("slot", selection.getSlotId());
            row.put("position", positionBySlot.get(selection.getSlotId()));
            row.put("roleId", selection.getRoleId());
            row.put("tags", Objects.isNull(selection.getTags()) ? List.of() : List.copyOf(selection.getTags()));
            slots.add(row);
        }
        Map<String, Long> lineup = Objects.isNull(request.getLineup()) ? null : request.getLineup().entrySet().stream()
                .filter(e -> positionBySlot.containsKey(e.getKey()) && Objects.nonNull(e.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
        TeamSetup setup = tactic.getSetup();
        String name = Objects.isNull(request.getName()) || request.getName().isBlank() ? DRAFT_NAME : request.getName().trim();
        return new UserTactic(slot, request.getCareerId(), name, formation.id(), Objects.isNull(setup) ? null : setup.getBuildUp(), null, null, slots,
                eaCode(tactic), null, false, lineup, Objects.isNull(setup) ? null : setup.getDepth(), tactic.getPresetId(), null, null);
    }

    /** EA kodu replikadaki gibi codec'le; formasyonun EA karşılığı yoksa kod boş kalır (kayıt engellenmez). */
    private String eaCode(TacticRequest tactic) {
        try {
            return tacticCodeService.export(tactic).getCode();
        } catch (FcareerRuntimeException e) {
            log.info("Draft tactic has no EA code formation : {} reason : {}", tactic.getFormation(), e.getMessage());
            return null;
        }
    }

    private UserTactic withAvailableLineup(Long userId, long careerId, UserTactic tactic) {
        if (Objects.isNull(tactic.lineup())) {
            return tactic.withLineup(null, 0);
        }
        Set<Long> available = new HashSet<>(careerService.squadPlayerIds(userId, careerId).playerIds());
        Map<String, Long> kept = new LinkedHashMap<>();
        tactic.lineup().forEach((slotId, playerId) -> {
            if (available.contains(playerId)) {
                kept.put(slotId, playerId);
            }
        });
        return tactic.withLineup(kept, tactic.lineup().size() - kept.size());
    }

    private static void requireSlot(int slot) {
        if (slot < MIN_SLOT || slot > MAX_SLOT) {
            throw new InvalidTacticException("slot " + slot);
        }
    }

    private void requireCareer(Long userId, Long careerId) {
        if (Objects.nonNull(careerId)) {
            careerService.requireOwned(userId, careerId);
        }
    }

    /** Ana taktik kavramı yalnız kariyerde (UT'de yok): careerId zorunlu (400), sahiplik (404). */
    private long requireCareerId(Long userId, Long careerId) {
        if (Objects.isNull(careerId)) {
            throw new InvalidTacticException("careerId required");
        }
        careerService.requireOwned(userId, careerId);
        return careerId;
    }
}
