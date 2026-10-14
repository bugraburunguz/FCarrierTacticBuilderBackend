package com.fcareer.recommendation.pipeline.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Kariyer öneri isteği (E3-6b): öneri girdisi (slot, mevki, rol, focus). Rol kaynağı sırası: user_tactic → mevcut kadro ataması → isteğin rolü → mevkinin varsayılan rolü.
 * Varsayılan olarak yalnız o rol/focus için puanlanır; alternativeRoles=true ise mevkideki tüm roller değerlendirilir ve her satırda rol yazılır.
 */
@Getter
@Setter
public class CareerRecommendationRequest {

    @NotBlank
    private String position;

    /** Taktikteki slot kimliği (user_tactic.slots[].slot); verilmezse mevkiye göre aranır. */
    private String slotId;

    /** Taban rol ("poacher") ya da bileşik kimlik ("poacher_attack"). */
    private String roleId;

    private String focus;

    /** true: isteğin rolü taktik/kadro atamasının önüne geçer. */
    private boolean forceRole;

    /** true: mevkideki tüm roller değerlendirilir (satırda rol açıkça yazılır). */
    private boolean alternativeRoles;

    /** BALANCED (varsayılan), NOW, FUTURE. */
    private String profile;

    @Min(1)
    @Max(30)
    private Integer limit;

    private Integer ageMin;

    private Integer ageMax;

    /** Yalnız simülasyon/rapor: kullanıcı takımı yerine bu LE takımının kadrosu ve gücü esas alınır (kayıttaki veri değişmez). */
    private Integer userTeamId;

    /** İstek bütçesi (€): kariyer bütçesinin yerine geçer; budgetUnknown=true ise bütçe hiç bilinmiyor sayılır. */
    private Long budgetEur;

    private boolean budgetUnknown;

    /** true: HAYAL kovasından en çok 3 oyuncu ayrı `longShots` dizisinde döner (ana listede HAYAL asla yoktur). */
    private boolean includeLongShots;
}
