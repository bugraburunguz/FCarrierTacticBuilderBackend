package com.fcareer.development.track;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * GTR-b: Gelişim satırı "Son {N} ayda: +3 OVR · 18 maç". Taban, son görüntüden en çok windowMonths önceki en eski görüntü; OVR farkı tabandan son
 * görüntüye, maç dönemin ara görüntülerinden (GrowthReport.periodPlay, sezon sıfırlaması dahil). Tabanda olmayan oyuncu satır almaz.
 */
public final class GrowthRecent {

    private static final double DAYS_PER_MONTH = 365.25 / 12;

    /** months en az 1; matches dönem maçı (bilinmiyorsa null). */
    public record Change(int months, int overallDelta, Integer matches) {
    }

    private GrowthRecent() {
    }

    /** dates eskiden yeniye. Pencere içinde son görüntüden önce görüntü yoksa -1. */
    public static int baseIndex(List<LocalDate> dates, int windowMonths) {
        if (dates.size() < 2) {
            return -1;
        }
        LocalDate since = dates.get(dates.size() - 1).minusMonths(windowMonths);
        for (int i = 0; i < dates.size() - 1; i++) {
            if (!dates.get(i).isBefore(since)) {
                return i;
            }
        }
        return -1;
    }

    /** Görüntülerde yalnız istenen oyuncular (kadro); hesap ve bellek 22 bin oyuncu yerine kadro kadar. */
    public static List<GrowthReport.Dated> only(List<GrowthReport.Dated> chain, Set<Long> playerIds) {
        return chain.stream().map((d) -> new GrowthReport.Dated(d.date(), d.players().stream().filter((p) -> playerIds.contains(p.playerId())).toList())).toList();
    }

    /**
     * chain tabandan son görüntüye; anahtar EA oyuncu kimliği. Ay en yakın aya yuvarlanır (en az 1). Maç, herhangi bir adımda oyuncunun ya da takımın
     * maç sayısı bilinmiyorsa null (periodPlay; ör. lig bağı olmayan takıma geçen oyuncu): satırda yalnız OVR yazar.
     */
    public static Map<Long, Change> compute(List<GrowthReport.Dated> chain) {
        Map<Long, Change> out = new HashMap<>();
        if (chain.size() < 2) {
            return out;
        }
        GrowthReport.Dated base = chain.get(0);
        GrowthReport.Dated last = chain.get(chain.size() - 1);
        int months = (int) Math.max(1, Math.round(ChronoUnit.DAYS.between(base.date(), last.date()) / DAYS_PER_MONTH));
        Map<Long, Integer> before = new HashMap<>();
        base.players().forEach((p) -> before.putIfAbsent(p.playerId(), p.overall()));
        Map<Long, int[]> play = GrowthReport.periodPlay(chain.stream().map(GrowthReport.Dated::players).toList());
        for (TrackedPlayer p : last.players()) {
            Integer was = before.get(p.playerId());
            if (Objects.nonNull(was)) {
                int[] sum = play.get(p.playerId());
                out.putIfAbsent(p.playerId(), new Change(months, p.overall() - was, Objects.isNull(sum) ? null : sum[0]));
            }
        }
        return out;
    }
}
