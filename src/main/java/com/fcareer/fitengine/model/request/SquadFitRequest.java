package com.fcareer.fitengine.model.request;

import com.fcareer.tactics.model.request.TacticRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SquadFitRequest {

    private Long careerId;

    @Size(max = 60)
    private List<Long> playerIds;

    private Short gameVersionId;

    @Valid
    @NotNull
    private TacticRequest tactic;

    /**
     * KARIYER-UX FAZ 2 (Q2), opsiyonel: slotId → oyuncu (base_player.id) sabit kalır, kalan slotları çözücü doldurur. Kadroda olmayan oyuncu ya da
     * formasyonda olmayan slot yok sayılır. Kilit varken sağ-sol aynalama yapılmaz (kilitli oyuncu kanat değiştirmesin).
     */
    @Size(max = 11)
    private Map<String, Long> locked;
}
