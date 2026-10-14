package com.fcareer.replica.model;

import com.fcareer.catalog.model.response.PlayerSummaryResponse;
import com.fcareer.tacticanalysis.TacticAnalysisModels.Analysis;

import java.util.List;

/** "Kadromla uygula" çıktısı: 11 slot (oyuncu + seviye rozeti) ve oyunculu analiz. source: CAREER | UT | SAMPLE. */
public record ReplicaApplyResponse(String replicaId, String source, double squadFit, List<Slot> slots, Analysis analysis) {

    /** state: GOOD | AT_LEVEL | WEAK | WRONG_ROLE | EMPTY; suggest: boş ya da zayıf slot için "öner" bağlantısı. */
    public record Slot(String slotId, String position, String roleId, String roleName, String focus, PlayerSummaryResponse player, Double currentFit, Double levelFit, String state, String badgeText,
                       boolean suggest) {
    }
}
