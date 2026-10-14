package com.fcareer.recommendation.pipeline;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Bileşik puan (E3-6b): değer/bütçe + hedef kulüp gücü − kullanıcı kulüp gücü + oyuncu OVR'si − kullanıcının ilk 11 ortalaması + kulüpteki rol + sözleşme.
 * Bilinmeyen girdi (null) oyuncunun aleyhine sayılmaz, gerekçede "bilinmiyor" yazar. Serbest oyuncu kulüp/değer/sözleşme kurallarından muaf ama OVR farkına tabidir.
 */
@Component
public class FeasibilityBuckets {

    /**
     * clubGap = hedef kulüp gücü − kullanıcı kulüp gücü (pozitif = hedef daha güçlü); ovrGap = oyuncunun OVR'si − kullanıcının ilk 11 OVR ortalaması;
     * clubRank = oyuncunun kendi takımındaki OVR sırası (1 = en iyi).
     */
    public record Input(Double valueRatio, Double clubGap, Double ovrGap, Integer clubRank, Integer contractMonthsLeft, boolean freeAgent) {
    }

    public record Factor(String name, String value, int points, String note) {
    }

    public record Result(FeasibilityBucket bucket, int points, List<Factor> factors) {
    }

    private final RecommendProperties.Feasibility config;

    public FeasibilityBuckets(RecommendProperties properties) {
        this.config = properties.getFeasibility();
    }

    public Result classify(Input in) {
        List<Factor> factors = new ArrayList<>();
        int total = 0;
        if (in.freeAgent()) {
            factors.add(new Factor("serbest", "evet", 0, "serbest oyuncu: bonservis bedeli, kulüp ve sözleşme engeli yok"));
        } else {
            total += add(factors, valueFactor(in.valueRatio()));
            total += add(factors, clubFactor(in.clubGap()));
        }
        total += add(factors, ovrFactor(in.ovrGap()));
        if (!in.freeAgent()) {
            total += add(factors, roleFactor(in.clubRank()));
            total += add(factors, contractFactor(in.contractMonthsLeft()));
        }
        total = Math.max(0, total);
        FeasibilityBucket bucket = total <= config.getTakeableMax() ? FeasibilityBucket.ALINABILIR : total <= config.getHardMax() ? FeasibilityBucket.ZOR : FeasibilityBucket.HAYAL;
        bucket = floor(bucket, in, factors);
        return new Result(bucket, total, factors);
    }

    /** Sert kurallar kovayı yükseltir (puan değişmez): OVR farkı ≥ ovrFantasyAt → HAYAL, ≥ ovrHardAt → en az ZOR; rakip kulübün ilk 11 oyuncusu → en az ZOR. */
    private FeasibilityBucket floor(FeasibilityBucket bucket, Input in, List<Factor> factors) {
        FeasibilityBucket result = bucket;
        if (Objects.nonNull(in.ovrGap()) && in.ovrGap() >= config.getOvrFantasyAt()) {
            factors.add(new Factor("OVR sınırı", String.format(Locale.ROOT, "%+.1f", in.ovrGap()), 0, String.format(Locale.ROOT, "ilk 11 ortalamasının %.0f+ OVR üstünde: hayal", config.getOvrFantasyAt())));
            return FeasibilityBucket.HAYAL;
        }
        if (Objects.nonNull(in.ovrGap()) && in.ovrGap() >= config.getOvrHardAt() && result == FeasibilityBucket.ALINABILIR) {
            factors.add(new Factor("OVR sınırı", String.format(Locale.ROOT, "%+.1f", in.ovrGap()), 0, String.format(Locale.ROOT, "ilk 11 ortalamasının %.0f+ OVR üstünde: en az zor", config.getOvrHardAt())));
            result = FeasibilityBucket.ZOR;
        }
        if (!in.freeAgent() && Objects.nonNull(in.clubGap()) && Objects.nonNull(in.clubRank()) && in.clubGap() >= config.getRivalClubGap() && in.clubRank() <= config.getRivalStarterRank()
                && result == FeasibilityBucket.ALINABILIR) {
            factors.add(new Factor("rakip kulübün ilk 11 oyuncusu", String.valueOf(in.clubRank()), 0, "benzer ya da güçlü kulübün ilk 11 oyuncusu: en az zor"));
            result = FeasibilityBucket.ZOR;
        }
        return result;
    }

    private static int add(List<Factor> factors, Factor factor) {
        factors.add(factor);
        return factor.points();
    }

    private Factor valueFactor(Double ratio) {
        if (Objects.isNull(ratio)) {
            return new Factor("değer/bütçe", "?", 0, "bütçe ya da değer bilinmiyor: puan yok");
        }
        int points = ratio <= config.getRatioA() ? 0 : ratio <= config.getRatioB() ? 1 : ratio <= config.getRatioC() ? 2 : 3;
        return new Factor("değer/bütçe", String.format(Locale.ROOT, "%.2f", ratio), points, String.format(Locale.ROOT, "değer bütçenin %%%.0f kadarı", ratio * 100));
    }

    private Factor clubFactor(Double gap) {
        if (Objects.isNull(gap)) {
            return new Factor("kulüp gücü farkı", "?", 0, "kulüp gücü bilinmiyor: puan yok");
        }
        int points = gap <= config.getClubGapA() ? 0 : gap <= config.getClubGapB() ? 1 : gap <= config.getClubGapC() ? 2 : 3;
        return new Factor("kulüp gücü farkı", String.format(Locale.ROOT, "%+.1f", gap), points,
                String.format(Locale.ROOT, "hedef kulüp, kendi kulübünden %+.1f OVR (ilk 16 ortalaması) %s", gap, gap > 0 ? "güçlü" : "zayıf"));
    }

    private Factor ovrFactor(Double gap) {
        if (Objects.isNull(gap)) {
            return new Factor("OVR farkı", "?", 0, "kadro seviyesi bilinmiyor: puan yok");
        }
        int points = gap <= config.getOvrGapA() ? 0 : gap <= config.getOvrGapB() ? 1 : gap <= config.getOvrGapC() ? 2 : 3;
        return new Factor("OVR farkı", String.format(Locale.ROOT, "%+.1f", gap), points,
                String.format(Locale.ROOT, "oyuncu, ilk 11 ortalamasından %+.1f OVR %s: bu seviyeye %s", gap, gap > 0 ? "yüksek" : "düşük", points == 0 ? "iner" : "inmesi zor"));
    }

    private Factor roleFactor(Integer rank) {
        if (Objects.isNull(rank)) {
            return new Factor("kulüpteki rol", "?", 0, "kulüp içi sıra bilinmiyor: puan yok");
        }
        int points = rank <= config.getKeyRank() ? config.getKeyPoints() : rank <= config.getStarterRank() ? config.getStarterPoints() : 0;
        String role = rank <= config.getKeyRank() ? "kilit oyuncu" : rank <= config.getStarterRank() ? "ilk kadro" : "yedek/rotasyon";
        return new Factor("kulüpteki rol", String.valueOf(rank), points, "kulüp içi OVR sırası " + rank + ": " + role);
    }

    private Factor contractFactor(Integer months) {
        if (Objects.isNull(months)) {
            return new Factor("sözleşme", "?", 0, "sözleşme süresi bilinmiyor: puan yok");
        }
        int points = months <= config.getExpiringMonths() ? -1 : months > config.getLongMonths() ? 1 : 0;
        return new Factor("sözleşme", String.valueOf(months), points, months + " ay kaldı");
    }
}
