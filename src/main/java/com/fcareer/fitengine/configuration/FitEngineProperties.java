package com.fcareer.fitengine.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "fcareer.fit")
public class FitEngineProperties {

    private Weapon weapon = new Weapon();
    private Projected projected = new Projected();
    private Playstyle playstyle = new Playstyle();
    private Accelerate accelerate = new Accelerate();
    private String roleWeightsVersion = "v1";
    /** Motor sürümü (v1|v3). engineVersion: katalog/UT yolları; careerEngineVersion: kariyer kadrosu yolları. Geri alma = bu değerleri v1 yapmak (E3-5). */
    private String engineVersion = "v1";
    private String careerEngineVersion = "v3";
    private V3 v3 = new V3();
    private DefensiveGate defensiveGate = new DefensiveGate();
    private double outOfPositionPenalty = 15;
    private double globalFloorPenaltyPerPoint = 0.8;
    private double intentWeaponBoost = 0.05;
    private Intent intent = new Intent();
    private Squad squad = new Squad();
    private Map<String, List<String>> positionEquivalents = defaultPositionEquivalents();
    private Map<String, List<String>> playstyleRelevance = defaultPlaystyleRelevance();

    /** v3 rozet eşikleri (levelFit yüzdeliği; yoksa currentFit): >=green iyi, >=neutral seviyesinde (renksiz), >=yellow zayıf, altı yanlış rol. verified:false, UX ayarı. */
    @Getter
    @Setter
    public static class V3 {
        private double greenFrom = 65;
        private double neutralFrom = 40;
        private double yellowFrom = 20;
        /** "Biçilmiş kaftan" rozeti yalnız levelFit bu değer ve üstündeyken (verified:false). */
        private double tailoredFrom = 85;
    }

    @Getter
    @Setter
    public static class Weapon {
        private double yellowPenaltyPerPoint = 0.4;
        private double redPenaltyPerPoint = 1.5;
        private int playstylePlusCompensation = 4;
    }

    @Getter
    @Setter
    public static class DefensiveGate {
        private boolean enabled = true;
        private double heavyPenalty = 30;
        private int projectedCap = 60;
        private Map<String, Integer> defFloor = Map.of("CB", 62, "FB", 55, "CDM", 60);
        private Map<String, Integer> tackleFloor = Map.of("CB", 60, "FB", 55, "CDM", 60);
    }

    @Getter
    @Setter
    public static class Projected {
        private int redCap = 74;
        private int yellowCap = 82;
        private double playstyleFactor = 0.4;
        private double accelerateFactor = 0.3;
    }

    @Getter
    @Setter
    public static class Playstyle {
        private double priority = 3;
        private double priorityPlus = 6;
        private double bonus = 1.5;
        private double bonusPlus = 3;
    }

    @Getter
    @Setter
    public static class Accelerate {
        private double match = 4;
        private double mismatch = -6;
        // FC26 için topluluk kaynaklarında belgelenen eşikler (fut.gg, fifauteam); FC27 için doğrulanmadı — CONFIG.
        private int explosiveMaxHeight = 182;
        private int explosiveMaxHeightWomen = 162;
        private int explosiveMinAgility = 65;
        private int explosiveMinAgilityMinusStrength = 10;
        private int explosiveMinAcceleration = 80;
        private int lengthyMinHeight = 183;
        private int lengthyMinHeightWomen = 164;
        private int lengthyMinStrength = 65;
        private int lengthyMinStrengthMinusAgility = 4;
        private int lengthyMinAcceleration = 40;
        private int lengthyMaxAgility = 65;
    }

    @Getter
    @Setter
    public static class Intent {
        private double okRatio = 0.85;
        private double weakRatio = 0.70;
    }

    @Getter
    @Setter
    public static class Squad {
        private double roleFitWeight = 0.7;
        private double intentFitWeight = 0.3;
        private int weakLinkCount = 3;
        private int depthCount = 2;
    }

    private static Map<String, List<String>> defaultPositionEquivalents() {
        Map<String, List<String>> map = new HashMap<>();
        return map;
    }

    private static Map<String, List<String>> defaultPlaystyleRelevance() {
        Map<String, List<String>> map = new HashMap<>();
        map.put("Finishing", List.of("FinesseShot", "PowerShot", "ChipShot", "LowDrivenShot", "Acrobatic"));
        map.put("ShotPower", List.of("PowerShot"));
        map.put("LongShots", List.of("PowerShot", "FinesseShot", "LowDrivenShot"));
        map.put("Curve", List.of("FinesseShot", "WhippedPass", "DeadBall"));
        map.put("Crossing", List.of("WhippedPass"));
        map.put("ShortPassing", List.of("TikiTaka", "IncisivePass", "PingedPass"));
        map.put("LongPassing", List.of("LongBallPass", "PingedPass"));
        map.put("Vision", List.of("IncisivePass", "Inventive"));
        map.put("Dribbling", List.of("Technical", "Rapid", "PressProven", "Trickster"));
        map.put("BallControl", List.of("FirstTouch", "Technical", "PressProven"));
        map.put("Agility", List.of("Technical", "Rapid"));
        map.put("Acceleration", List.of("QuickStep", "Rapid"));
        map.put("SprintSpeed", List.of("QuickStep", "Rapid"));
        map.put("DefAwareness", List.of("Anticipate", "Intercept"));
        map.put("Interceptions", List.of("Intercept"));
        map.put("StandingTackle", List.of("Anticipate", "Jockey"));
        map.put("SlidingTackle", List.of("SlideTackle"));
        map.put("Strength", List.of("Bruiser"));
        map.put("Aggression", List.of("Bruiser"));
        map.put("Heading", List.of("PrecisionHeader", "AerialFortress"));
        map.put("Jumping", List.of("AerialFortress"));
        map.put("Stamina", List.of("Relentless"));
        map.put("Composure", List.of("FinesseShot", "PressProven"));
        map.put("GKReflexes", List.of("QuickReflexes", "FarReach"));
        map.put("GKPositioning", List.of("RushOut", "CrossClaimer"));
        map.put("GKKicking", List.of("Footwork", "FarThrow"));
        map.put("GKDiving", List.of("FarReach", "Deflector"));
        return map;
    }
}
