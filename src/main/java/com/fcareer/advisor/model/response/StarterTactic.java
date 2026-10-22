package com.fcareer.advisor.model.response;

import com.fcareer.fitengine.model.TeamSetup;

import java.util.List;
import java.util.Map;

/**
 * Ücretsiz otomatik başlangıç önerisi (D-31): ana taktik değildir. slots: formasyonun varsayılan rolleri; lineup: slotId → base_player.id (/fit/lineup);
 * alternatives: formasyon-only sıralamada 2. ve 3. formasyon. Ücretli içgörü (kurallar, NLG, preset) yok.
 */
public record StarterTactic(String formationId, TeamSetup setup, List<Slot> slots, Map<String, Long> lineup, double squadFit, List<Alternative> alternatives) {

    public record Slot(String slotId, String position, String roleId, String roleName) {
    }

    public record Alternative(String formationId, double score) {
    }
}
