package com.fcareer.catalog.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlayerSummaryResponse {
    private Long id;
    private String name;
    private Integer age;
    private int overall;
    private List<String> positions;
    private Integer clubId;
    private String club;
    private String league;
    private String nationality;
    private Integer gender;
    private String preferredFoot;
    private Integer weakFoot;
    private Integer skillMoves;
    private String accelerate;
    private Integer runStyle;
    private Integer potential;
    /** Spoiler kontrolü açıkken (SCOUT_RANGE) tam potansiyel yerine aralık: ör. 80–84. */
    private String potentialRange;
    private Long valueEur;
    private boolean valuationEstimated;
    private boolean freeAgent;
    /** 6 yüz istatistiği (saha: PAC/SHO/PAS/DRI/DEF/PHY, kaleci: DIV/HAN/KIC/REF/SPD/POS); faceLabels sırasıyla. */
    private java.util.List<Integer> faceStats;
    private java.util.List<String> faceLabels;
    /** OFFICIAL (EA ratings sitesi) | COMPUTED (ut_rules ağırlıklarıyla). */
    private String faceStatsSource;
    /** Hesaplanan değer ve ağırlıklar doğrulanmadıysa true: arayüz "≈" gösterir. */
    private boolean faceStatsApproximate;
    private Long faceAssetId;
    private String faceUrl;
    private Boolean changed;
    private com.fcareer.versioning.model.VersionModels.ChangeSummary changeSummary;
    /** Kariyer kaydındaki sözleşme bitişi (V3-06a; kayıtta yoksa alan yok). */
    private java.time.LocalDate contractUntil;
    /** Kariyerde başka kulübe kiralık (V3-06a; değilse alan yok). */
    private Boolean onLoan;
    /** Ulaşılabilirlik kovası: ALINABILIR / ZOR / HAYAL (V3-06a; yalnız kariyer listesinde, öneri hattının sınıflandırıcısı). */
    private String feasibility;
    /** Seçili rolde FitEngineV3 anlık uyum (V3-06a; yalnız rol sıralamasında). */
    private Double roleFit;
}
