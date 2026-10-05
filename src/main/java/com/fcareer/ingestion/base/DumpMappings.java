package com.fcareer.ingestion.base;

import com.fcareer.fitengine.model.Attr;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Live Editor dökümündeki kod → anlam eşlemeleri. Hepsi 19 bin eşleşen oyuncuda katalogla karşılaştırılarak
 * türetildi ve doğrulandı (her kodda %95+ uyum).
 */
public final class DumpMappings {

    /** birthdate (gün sayısı) + bu sabit = 1970-01-01'den beri gün. */
    public static final long BIRTHDATE_EPOCH_OFFSET = -141428L;

    public static final int RIGHT_FOOT = 1;
    public static final int LEFT_FOOT = 2;
    public static final int SKILL_MOVES_OFFSET = 1;
    public static final int POSITION_SLOTS = 7;
    public static final int NO_POSITION = -1;

    public static final Map<Integer, String> POSITION_BY_CODE = Map.ofEntries(
            Map.entry(0, "GK"), Map.entry(3, "RB"), Map.entry(5, "CB"), Map.entry(7, "LB"),
            Map.entry(10, "CDM"), Map.entry(12, "RM"), Map.entry(14, "CM"), Map.entry(16, "LM"),
            Map.entry(18, "CAM"), Map.entry(23, "RW"), Map.entry(25, "ST"), Map.entry(27, "LW"));

    /** trait1 / icontrait1 bit sırası → PlayStyle id'si. */
    public static final String[] OUTFIELD_PLAYSTYLES = {
            "FinesseShot", "ChipShot", "PowerShot", "DeadBall", "PrecisionHeader", "Acrobatic", "LowDrivenShot",
            "Gamechanger", "IncisivePass", "PingedPass", "LongBallPass", "TikiTaka", "WhippedPass", "Inventive",
            "Jockey", "Block", "Intercept", "Anticipate", "SlideTackle", "AerialFortress", "Technical", "Rapid",
            "FirstTouch", "Trickster", "PressProven", "QuickStep", "Relentless", "LongThrow", "Bruiser", "Enforcer"};

    /** trait2 / icontrait2 bit sırası → kaleci PlayStyle id'si. */
    public static final String[] GOALKEEPER_PLAYSTYLES = {"FarThrow", "Footwork", "CrossClaimer", "RushOut", "FarReach", "Deflector"};

    public static final Map<String, Attr> ATTRIBUTE_COLUMNS = attributeColumns();

    private DumpMappings() {
    }

    private static Map<String, Attr> attributeColumns() {
        Map<String, Attr> map = new LinkedHashMap<>();
        map.put("acceleration", Attr.ACCELERATION);
        map.put("sprintspeed", Attr.SPRINT_SPEED);
        map.put("agility", Attr.AGILITY);
        map.put("balance", Attr.BALANCE);
        map.put("reactions", Attr.REACTIONS);
        map.put("ballcontrol", Attr.BALL_CONTROL);
        map.put("dribbling", Attr.DRIBBLING);
        map.put("composure", Attr.COMPOSURE);
        map.put("vision", Attr.VISION);
        map.put("shortpassing", Attr.SHORT_PASSING);
        map.put("longpassing", Attr.LONG_PASSING);
        map.put("crossing", Attr.CROSSING);
        map.put("curve", Attr.CURVE);
        map.put("freekickaccuracy", Attr.FK_ACCURACY);
        map.put("finishing", Attr.FINISHING);
        map.put("shotpower", Attr.SHOT_POWER);
        map.put("longshots", Attr.LONG_SHOTS);
        map.put("volleys", Attr.VOLLEYS);
        map.put("penalties", Attr.PENALTIES);
        map.put("positioning", Attr.POSITIONING);
        map.put("headingaccuracy", Attr.HEADING);
        map.put("defensiveawareness", Attr.DEF_AWARENESS);
        map.put("standingtackle", Attr.STANDING_TACKLE);
        map.put("slidingtackle", Attr.SLIDING_TACKLE);
        map.put("interceptions", Attr.INTERCEPTIONS);
        map.put("jumping", Attr.JUMPING);
        map.put("stamina", Attr.STAMINA);
        map.put("strength", Attr.STRENGTH);
        map.put("aggression", Attr.AGGRESSION);
        map.put("gkdiving", Attr.GK_DIVING);
        map.put("gkhandling", Attr.GK_HANDLING);
        map.put("gkkicking", Attr.GK_KICKING);
        map.put("gkpositioning", Attr.GK_POSITIONING);
        map.put("gkreflexes", Attr.GK_REFLEXES);
        return map;
    }
}
