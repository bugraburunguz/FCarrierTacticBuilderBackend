package com.fcareer.recommendation.pipeline;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Öneri hattı (13 §3.3) katsayıları. Hepsi `verified:false` ilk kalibrasyondur; gerçek senaryo raporuna (E3-6_crewe_oneriler.md) göre ayarlanır. */
@Getter
@Setter
@ConfigurationProperties(prefix = "fcareer.recommend")
public class RecommendProperties {

    /** SQL aday havuzu üst sınırı (önskor sırasıyla). */
    private int candidatePool = 2000;
    private int defaultLimit = 10;
    private int maxLimit = 30;
    /** Değer tavanı = bütçe × alpha. Bütçe bilinmiyorsa (null) değer filtresi yoktur; 0 da "bilinmiyor"dur. */
    private double valueAlpha = 1.25;
    /** Sözleşmesi bu kadar ay içinde biten oyuncular değer tavanından bağımsız edinilebilirdir. */
    private int contractMonthsMax = 6;
    /** Pozisyon uyumu: kullanılan en düşük posDist çarpanı. */
    private double minPositionFactor = 0.85;
    /** Aday lig seviyesi kullanıcınınkinden en fazla bu kadar üstte olabilir (tier 1 = en iyi); tablo `seed/league-tiers.json`. */
    private int maxTierJump = 2;
    private int youngAgeMax = 21;
    /** "Hemen hazır": levelFit ≥ readyLevelFit ve currentFit ≥ mevkideki mevcut oyuncunun currentFit'i. */
    private double readyLevelFit = 65;
    private int mmrPool = 60;
    private double mmrLambda = 0.7;
    private Feasibility feasibility = new Feasibility();
    private RankPenalty rankPenalty = new RankPenalty();

    @Getter
    @Setter
    public static class Feasibility {
        /** değer/bütçe oranı eşikleri: ≤ ratioA → 0 puan, ≤ ratioB → 1, ≤ ratioC → 2, üstü 3. */
        private double ratioA = 0.6;
        private double ratioB = 1.0;
        private double ratioC = 2.0;
        /** Lig seviye farkı (aday tier − kullanıcı tier): 0 ve üstü 0 puan; -1 → tierOne; ≤ -2 → tierTwoOrMore. */
        private int tierOne = 1;
        private int tierTwoOrMore = 3;
        /** Kulübündeki rol: takım içi OVR sırası ≤ keyRank → keyPoints, ≤ starterRank → starterPoints. */
        private int keyRank = 3;
        private int keyPoints = 2;
        private int starterRank = 14;
        private int starterPoints = 1;
        /** Sözleşme: ≤ expiringMonths → -1, > longMonths → +1. */
        private int expiringMonths = 6;
        private int longMonths = 36;
        /** Toplam puan: ≤ takeableMax ALINABİLİR, ≤ hardMax ZOR, üstü HAYAL. */
        private int takeableMax = 1;
        private int hardMax = 4;
    }

    @Getter
    @Setter
    public static class RankPenalty {
        /** Sıralama puanı = currentFit − kova cezası. */
        private double hard = 6;
        private double fantasy = 20;
    }
}
