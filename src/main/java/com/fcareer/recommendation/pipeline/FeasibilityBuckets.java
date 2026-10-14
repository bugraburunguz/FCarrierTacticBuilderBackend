package com.fcareer.recommendation.pipeline;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Bileşik puan: değer/bütçe + lig seviye farkı + kulüpteki rol + sözleşme. Bilinmeyen girdi (null) oyuncunun aleyhine sayılmaz, gerekçede "bilinmiyor" yazar. */
@Component
public class FeasibilityBuckets {

    /** tierGap = aday lig seviyesi − kullanıcı lig seviyesi (negatif = aday daha üst ligde); clubRank = oyuncunun kendi takımındaki OVR sırası (1 = en iyi). */
    public record Input(Double valueRatio, Integer tierGap, Integer clubRank, Integer contractMonthsLeft, boolean freeAgent) {
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
        if (in.freeAgent()) {
            factors.add(new Factor("serbest", "evet", 0, "serbest oyuncu: bonservis bedeli yok"));
            return new Result(FeasibilityBucket.ALINABILIR, 0, factors);
        }
        int total = 0;
        total += add(factors, valueFactor(in.valueRatio()));
        total += add(factors, tierFactor(in.tierGap()));
        total += add(factors, roleFactor(in.clubRank()));
        total += add(factors, contractFactor(in.contractMonthsLeft()));
        total = Math.max(0, total);
        FeasibilityBucket bucket = total <= config.getTakeableMax() ? FeasibilityBucket.ALINABILIR : total <= config.getHardMax() ? FeasibilityBucket.ZOR : FeasibilityBucket.HAYAL;
        return new Result(bucket, total, factors);
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

    private Factor tierFactor(Integer gap) {
        if (Objects.isNull(gap)) {
            return new Factor("lig seviyesi", "?", 0, "lig seviyesi bilinmiyor: puan yok");
        }
        int points = gap >= 0 ? 0 : gap == -1 ? config.getTierOne() : config.getTierTwoOrMore();
        return new Factor("lig seviyesi", String.valueOf(gap), points, gap >= 0 ? "aday kendi ligin ya da daha alt seviyede" : -gap + " seviye üst ligde");
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
