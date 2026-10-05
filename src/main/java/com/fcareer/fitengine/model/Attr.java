package com.fcareer.fitengine.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public enum Attr {
    ACCELERATION("Acceleration", "acceleration"),
    SPRINT_SPEED("SprintSpeed", "sprint_speed"),
    AGILITY("Agility", "agility"),
    BALANCE("Balance", "balance"),
    REACTIONS("Reactions", "reactions"),
    BALL_CONTROL("BallControl", "ball_control"),
    DRIBBLING("Dribbling", "dribbling"),
    COMPOSURE("Composure", "composure"),
    VISION("Vision", "vision"),
    SHORT_PASSING("ShortPassing", "short_passing"),
    LONG_PASSING("LongPassing", "long_passing"),
    CROSSING("Crossing", "crossing"),
    CURVE("Curve", "curve"),
    FK_ACCURACY("FKAccuracy", "fk_accuracy"),
    FINISHING("Finishing", "finishing"),
    SHOT_POWER("ShotPower", "shot_power"),
    LONG_SHOTS("LongShots", "long_shots"),
    VOLLEYS("Volleys", "volleys"),
    PENALTIES("Penalties", "penalties"),
    POSITIONING("Positioning", "att_position"),
    HEADING("Heading", "heading"),
    DEF_AWARENESS("DefAwareness", "def_awareness"),
    STANDING_TACKLE("StandingTackle", "standing_tackle"),
    SLIDING_TACKLE("SlidingTackle", "sliding_tackle"),
    INTERCEPTIONS("Interceptions", "interceptions"),
    JUMPING("Jumping", "jumping"),
    STAMINA("Stamina", "stamina"),
    STRENGTH("Strength", "strength"),
    AGGRESSION("Aggression", "aggression"),
    GK_DIVING("GKDiving", "gk_diving"),
    GK_HANDLING("GKHandling", "gk_handling"),
    GK_KICKING("GKKicking", "gk_kicking"),
    GK_POSITIONING("GKPositioning", "gk_positioning"),
    GK_REFLEXES("GKReflexes", "gk_reflexes");

    private static final Map<String, Attr> BY_KEY = Arrays.stream(values())
            .collect(Collectors.toMap(a -> a.key.toLowerCase(), Function.identity()));

    private final String key;
    private final String column;

    public static Optional<Attr> fromKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_KEY.get(key.toLowerCase()));
    }

    public static Attr requireKey(String key) {
        return fromKey(key).orElseThrow(() -> new IllegalArgumentException("Unknown attribute key: " + key));
    }

    public String getMessageKey() {
        return "attr." + key;
    }
}
