package com.fcareer.catalog.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import com.fcareer.fitengine.model.Attr;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class PlayerSearchRequest {

    private Short gameVersionId;
    private String query;
    @Builder.Default
    private List<String> positions = new ArrayList<>();
    @Builder.Default
    private List<String> playstyles = new ArrayList<>();
    /** KARIYER-UX 6: PlayStyle+ (seviye ≥ 2) istenenler; hepsi birden (AND). */
    @Builder.Default
    private List<String> playstylesPlus = new ArrayList<>();
    @Builder.Default
    private List<String> accelerate = new ArrayList<>();
    @Builder.Default
    private Map<String, Integer> attrMin = new HashMap<>();
    @Builder.Default
    private Map<String, Integer> attrMax = new HashMap<>();
    private Integer overallMin;
    private Integer overallMax;
    private Integer potentialMin;
    private Long valueMax;
    /** V3-06a: değer alt sınırı (değeri bilinmeyen satır eşleşmez). */
    private Long valueMin;
    /** V3-06a: yalnız başka kulübe kiralık oyuncular (kariyer kaydı). */
    private boolean onLoan;
    /** V3-06a: sözleşmesi kayıttaki oyun tarihinden en çok N ay sonra biten oyuncular. */
    private Integer contractMonthsLeft;
    private Integer clubId;
    private Long careerId;
    private Integer teamId;
    private boolean freeAgent;
    private Integer leagueId;
    private List<Integer> leagueIds;
    private Integer nationalityId;
    private Short gender;
    private Integer ageMin;
    private Integer ageMax;
    private Integer potentialMax;
    private String foot;
    private Integer weakFootMin;
    private Integer skillMovesMin;
    private Long idFilter;
    private List<Long> ids;
    private List<Long> excludeIds;
    private boolean includeUnvalued;
    /** E3-6 aday havuzu: sort="prescore" iken Σ w_i·attr_i (bağlı parametre) sıralaması; OVR sıralaması yoktur. */
    private Map<Attr, Double> prescoreWeights;
    /** Kayıt dosyasında (live) oyuncusu olan kariyerde yalnız kayıttakiler; hiç live satırı yoksa tüm havuz. */
    private boolean livePoolOnly;
    /** Kiralıktaki (başka kulübe bağlı) oyuncular hariç. */
    private boolean availableOnly;
    /** Edinilebilir: değer ≤ tavan VEYA serbest VEYA sözleşmesi contractMonthsMax içinde biten. Tavan null ise koşul yok (bütçe bilinmiyor ≠ 0). */
    private Long acquirableValueCap;
    private Integer contractMonthsMax;
    private String sort;
    private boolean ascending;
    private int page;
    private int size;
}
