package com.fcareer.recommendation.pipeline;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/** Öneri hattı (13 §3.3) katsayıları. Hepsi `verified:false` ilk kalibrasyondur; gerçek senaryo raporlarına (E3-6_crewe_oneriler.md, E3-6b_oneriler.md) göre ayarlanır. */
@Getter
@Setter
@ConfigurationProperties(prefix = "fcareer.recommend")
public class RecommendProperties {

    /** Her taban rol sorgusunun SQL aday havuzu üst sınırı (önskor sırasıyla). */
    private int candidatePool = 2000;
    private int defaultLimit = 10;
    private int maxLimit = 30;
    /** Değer tavanı = bütçe × alpha. Bütçe bilinmiyorsa (null) değer filtresi yoktur; 0 da "bilinmiyor"dur. */
    private double valueAlpha = 1.25;
    /** Sözleşmesi bu kadar ay içinde biten oyuncular değer tavanından bağımsız edinilebilirdir. */
    private int contractMonthsMax = 6;
    /** Pozisyon uyumu: kullanılan en düşük posDist çarpanı. */
    private double minPositionFactor = 0.85;
    /** Bilinen ligler için tier tablosu (`seed/league-tiers.json`) sert eleme: aday lig seviyesi kullanıcınınkinden en fazla bu kadar üstte olabilir; bilinmeyen ligler elenmez. */
    private int maxTierJump = 2;
    private int youngAgeMax = 21;
    /** "Hemen hazır": levelFit ≥ readyLevelFit ve currentFit ≥ mevkideki mevcut oyuncunun aynı rol/focus'taki currentFit'i. */
    private double readyLevelFit = 65;
    private int mmrPool = 60;
    private double mmrLambda = 0.7;
    /** Mevki dışı (doğal mevkisi hedef olmayan) adayların ilk listede en fazla sayısı. */
    private int maxOutOfPosition = 2;
    /** Kulüp gücü: kayıttaki kulübün en iyi N oyuncusunun OVR ortalaması; en az minClubPlayers oyuncusu yoksa lig gücü kullanılır. */
    private int clubStrengthTop = 16;
    private int minClubPlayers = 11;
    /** Kullanıcının "ilk 11" ortalaması için oyuncu sayısı. */
    private int firstEleven = 11;
    private String defaultProfile = "BALANCED";
    /** Mevkinin varsayılan rolü (kadroda o mevkide oyuncu yoksa). Taban rol kimliği. */
    private Map<String, String> defaultRoles = defaultRoles();
    private Map<String, Profile> profiles = profiles();
    private Feasibility feasibility = new Feasibility();
    private RankPenalty rankPenalty = new RankPenalty();

    private static Map<String, String> defaultRoles() {
        Map<String, String> m = new HashMap<>();
        m.put("GK", "goalkeeper");
        m.put("RB", "fullback");
        m.put("LB", "fullback");
        m.put("CB", "defender");
        m.put("CDM", "holding");
        m.put("CM", "box_to_box");
        m.put("CAM", "playmaker");
        m.put("RM", "winger");
        m.put("LM", "winger");
        m.put("RW", "winger");
        m.put("LW", "winger");
        m.put("ST", "advanced_forward");
        return m;
    }

    private static Map<String, Profile> profiles() {
        Map<String, Profile> m = new HashMap<>();
        m.put("BALANCED", new Profile(30, 1.5, 0.35, 34, 4, 27));
        m.put("NOW", new Profile(33, 0.6, 0.10, 36, 0, 27));
        m.put("FUTURE", new Profile(99, 0.0, 0.60, 23, 0, 23));
        return m;
    }

    /** Yaş profili: ceza ageRiskFrom yaşında başlar; futureWeight sıralamada futureFit ağırlığı; maxAge varsayılan üst yaş; minPrime listede en az bu kadar oyuncu ≤ primeAge (havuzda varsa). */
    @Getter
    @Setter
    public static class Profile {
        private int ageRiskFrom;
        private double ageRiskPerYear;
        private double futureWeight;
        private int maxAge;
        private int minPrime;
        private int primeAge;

        public Profile() {
        }

        public Profile(int ageRiskFrom, double ageRiskPerYear, double futureWeight, int maxAge, int minPrime, int primeAge) {
            this.ageRiskFrom = ageRiskFrom;
            this.ageRiskPerYear = ageRiskPerYear;
            this.futureWeight = futureWeight;
            this.maxAge = maxAge;
            this.minPrime = minPrime;
            this.primeAge = primeAge;
        }
    }

    @Getter
    @Setter
    public static class Feasibility {
        /** değer/bütçe oranı eşikleri: ≤ ratioA → 0 puan, ≤ ratioB → 1, ≤ ratioC → 2, üstü 3. */
        private double ratioA = 0.6;
        private double ratioB = 1.0;
        private double ratioC = 2.0;
        /** Kulüp gücü farkı (hedef − kullanıcı): ≤ clubGapA → 0, ≤ clubGapB → 1, ≤ clubGapC → 2, üstü 3. */
        private double clubGapA = 2.0;
        private double clubGapB = 5.0;
        private double clubGapC = 9.0;
        /** Oyuncu OVR'si − kullanıcının ilk 11 ortalaması: ≤ ovrGapA → 0, ≤ ovrGapB → 1, ≤ ovrGapC → 2, üstü 3. */
        private double ovrGapA = 3.0;
        private double ovrGapB = 6.0;
        private double ovrGapC = 10.0;
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
        /** Sıralama puanı = (1−w)·currentFit + w·futureFit − kova cezası − yaş riski. */
        private double hard = 6;
        private double fantasy = 20;
        /** Oyuncu OVR'si ilk 11 ortalamasının belowSquadFloor puandan fazla altındaysa her ek puan için belowSquadPerPoint ceza (kullanıcı için yedek kalitesi). */
        private double belowSquadFloor = 8;
        private double belowSquadPerPoint = 1.5;

        public double belowSquad(Double ovrGap) {
            if (ovrGap == null || ovrGap >= -belowSquadFloor) {
                return 0;
            }
            return (-ovrGap - belowSquadFloor) * belowSquadPerPoint;
        }
    }
}
