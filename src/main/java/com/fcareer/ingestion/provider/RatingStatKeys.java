package com.fcareer.ingestion.provider;

import com.fcareer.fitengine.model.Attr;

import java.util.EnumMap;
import java.util.Map;

public final class RatingStatKeys {

    public static final Map<Attr, String> KEYS = new EnumMap<>(Attr.class);

    static {
        KEYS.put(Attr.ACCELERATION, "acceleration");
        KEYS.put(Attr.SPRINT_SPEED, "sprintSpeed");
        KEYS.put(Attr.AGILITY, "agility");
        KEYS.put(Attr.BALANCE, "balance");
        KEYS.put(Attr.REACTIONS, "reactions");
        KEYS.put(Attr.BALL_CONTROL, "ballControl");
        KEYS.put(Attr.DRIBBLING, "dribbling");
        KEYS.put(Attr.COMPOSURE, "composure");
        KEYS.put(Attr.VISION, "vision");
        KEYS.put(Attr.SHORT_PASSING, "shortPassing");
        KEYS.put(Attr.LONG_PASSING, "longPassing");
        KEYS.put(Attr.CROSSING, "crossing");
        KEYS.put(Attr.CURVE, "curve");
        KEYS.put(Attr.FK_ACCURACY, "freeKickAccuracy");
        KEYS.put(Attr.FINISHING, "finishing");
        KEYS.put(Attr.SHOT_POWER, "shotPower");
        KEYS.put(Attr.LONG_SHOTS, "longShots");
        KEYS.put(Attr.VOLLEYS, "volleys");
        KEYS.put(Attr.PENALTIES, "penalties");
        KEYS.put(Attr.POSITIONING, "positioning");
        KEYS.put(Attr.HEADING, "headingAccuracy");
        KEYS.put(Attr.DEF_AWARENESS, "defensiveAwareness");
        KEYS.put(Attr.STANDING_TACKLE, "standingTackle");
        KEYS.put(Attr.SLIDING_TACKLE, "slidingTackle");
        KEYS.put(Attr.INTERCEPTIONS, "interceptions");
        KEYS.put(Attr.JUMPING, "jumping");
        KEYS.put(Attr.STAMINA, "stamina");
        KEYS.put(Attr.STRENGTH, "strength");
        KEYS.put(Attr.AGGRESSION, "aggression");
        KEYS.put(Attr.GK_DIVING, "gkDiving");
        KEYS.put(Attr.GK_HANDLING, "gkHandling");
        KEYS.put(Attr.GK_KICKING, "gkKicking");
        KEYS.put(Attr.GK_POSITIONING, "gkPositioning");
        KEYS.put(Attr.GK_REFLEXES, "gkReflexes");
    }

    private RatingStatKeys() {
    }
}
