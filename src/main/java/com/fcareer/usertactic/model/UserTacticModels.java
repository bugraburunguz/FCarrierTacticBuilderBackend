package com.fcareer.usertactic.model;

import com.fcareer.tactics.model.request.TacticRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class UserTacticModels {

    private UserTacticModels() {
    }

    /**
     * Slota kaydet: ya replikadan (replicaId; kod ve ayarlar replikadan) ya taslaktan (tactic = tek taktik gövdesi TacticRequest, + opsiyonel diziliş).
     * İkisi birden ya da hiçbiri 400. careerId boşsa UT taktiğidir.
     */
    @Getter
    @Setter
    public static class SaveRequest {
        private Long careerId;
        private String replicaId;
        @Size(max = 80)
        private String name;
        @Valid
        private TacticRequest tactic;
        /** slotId → base_player.id (/fit/lineup'ın playerId'si). */
        private Map<String, Long> lineup;
    }

    /**
     * slots: [{slot, position, roleId, focus?, tags[]}] (TacticSlotLookup biçimi; replika kayıtlarında tags boş dizi).
     * main: kariyerin ana taktiği (D-30). lineupDropped yalnız ana taktik okumasında: kadroda olmadığı için dizilişten düşen oyuncu sayısı.
     */
    public record UserTactic(int slot, Long careerId, String name, String formation, String buildUp, String defensiveApproach, Integer lineHeight,
                             List<Map<String, Object>> slots, String eaCode, String sourceReplicaId, boolean main, Map<String, Long> lineup,
                             Integer depth, String presetId, OffsetDateTime updatedAt, Integer lineupDropped) {

        public UserTactic withLineup(Map<String, Long> filtered, int dropped) {
            return new UserTactic(slot, careerId, name, formation, buildUp, defensiveApproach, lineHeight, slots, eaCode, sourceReplicaId, main, filtered,
                    depth, presetId, updatedAt, dropped);
        }
    }
}
