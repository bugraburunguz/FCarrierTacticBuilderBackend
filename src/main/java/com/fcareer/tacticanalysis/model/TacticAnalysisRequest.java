package com.fcareer.tacticanalysis.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Taktik analizi isteği (E3-7): formasyon + build-up + hat + slot rolleri (rol_focus kimliği; verilmeyen slot formasyonun varsayılan rolünü alır). Etiket YOK.
 * careerId + assigned (slotId → oyuncu kimliği) verilirse oyuncu attribute'larıyla birleşir; kariyer sahipliği doğrulanır.
 */
@Getter
@Setter
public class TacticAnalysisRequest {

    @NotBlank
    private String formation;

    /** Short | Balanced | Counter (boşsa Balanced). */
    private String buildUp;

    @Min(1)
    @Max(100)
    private Integer depth;

    @Valid
    @Size(max = 11)
    private List<SlotRole> slots = new ArrayList<>();

    private Long careerId;

    private Map<String, Long> assigned;

    @Getter
    @Setter
    public static class SlotRole {
        @NotBlank
        private String slotId;
        @NotBlank
        private String roleId;
    }
}
