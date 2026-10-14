package com.fcareer.usertactic.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

public final class UserTacticModels {

    private UserTacticModels() {
    }

    /** Replikadan kaydet: kod ve ayarlar replikadan kopyalanır. careerId boşsa UT taktiğidir. */
    @Getter
    @Setter
    public static class SaveRequest {
        private Long careerId;
        @NotBlank
        private String replicaId;
    }

    /** slots: [{slot, position, roleId, focus}] (TacticSlotLookup biçimi). */
    public record UserTactic(int slot, Long careerId, String name, String formation, String buildUp, String defensiveApproach, Integer lineHeight, List<Map<String, String>> slots, String eaCode,
                             String sourceReplicaId) {
    }
}
