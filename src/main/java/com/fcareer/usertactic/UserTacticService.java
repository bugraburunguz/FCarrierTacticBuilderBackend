package com.fcareer.usertactic;

import com.fcareer.career.service.CareerService;
import com.fcareer.common.exception.InvalidTacticException;
import com.fcareer.common.exception.NotFoundException;
import com.fcareer.replica.ReplicaService;
import com.fcareer.replica.ReplicaService.Replica;
import com.fcareer.usertactic.model.UserTacticModels.SaveRequest;
import com.fcareer.usertactic.model.UserTacticModels.UserTactic;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** "Taktik slotuma kaydet" (REP-07): replikanın kodu ve ayarları kullanıcının 5 slotundan birine kopyalanır (source_replica_id dolu). */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserTacticService {

    private static final int MIN_SLOT = 1;
    private static final int MAX_SLOT = 5;

    private final UserTacticRepository repository;
    private final ReplicaService replicas;
    private final CareerService careerService;

    public List<UserTactic> list(Long userId, Long careerId) {
        requireCareer(userId, careerId);
        return repository.list(userId, careerId);
    }

    @Transactional
    public UserTactic save(Long userId, int slot, SaveRequest request) {
        requireSlot(slot);
        requireCareer(userId, request.getCareerId());
        Replica replica = replicas.find(request.getReplicaId()).orElseThrow(() -> new NotFoundException("replica", request.getReplicaId()));
        List<Map<String, String>> slots = new ArrayList<>();
        replica.slots().forEach((s) -> {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("slot", s.slotId());
            row.put("position", s.position());
            row.put("roleId", s.roleId());
            row.put("focus", s.focus());
            slots.add(row);
        });
        UserTactic tactic = new UserTactic(slot, request.getCareerId(), replica.coach() + " — " + replica.club() + " " + replica.season(), replica.formationId(), replica.buildUp(),
                replica.defensiveApproach(), replica.lineHeight(), slots, replica.tacticCode(), replica.id());
        repository.upsert(userId, tactic);
        log.info("User tactic saved slot : {} replica : {} career : {}", slot, replica.id(), request.getCareerId());
        return tactic;
    }

    @Transactional
    public void delete(Long userId, Long careerId, int slot) {
        requireSlot(slot);
        requireCareer(userId, careerId);
        repository.delete(userId, careerId, slot);
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
}
