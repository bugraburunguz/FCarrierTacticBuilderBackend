package com.fcareer.development.track;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** GTR-a (verified:false): anlık görüntü sınırı ve ölçüm raporunun oynama kovası eşikleri. Model değerleri bu işte değişmez. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "fcareer.growth-track")
public class GrowthTrackProperties {
    /** Kariyer içi içe aktarmada anlık görüntü yazılır mı. */
    private boolean enabled = true;
    /** Kariyer başına en çok anlık görüntü; aşılırsa en eskiler seyreltilir. */
    private int maxSnapshots = 60;
    /** Seyreltmede korunan en yeni görüntü sayısı (en eski görüntü de hep korunur). */
    private int keepRecent = 12;
    /** Oynama kovası: dönemde oyuncu maçı / takım maçı oranı. 0 → oynamadı; bu orandan küçük → az; büyük ya da eşit → düzenli. */
    private double regularFromRatio = 0.5;
    /** GTR-b: Gelişim satırının penceresi (ay); taban, son görüntüden en çok bu kadar önceki en eski görüntü. */
    private int recentWindowMonths = 6;
}
