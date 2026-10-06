package com.fcareer.fitengine.service;

import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.IntentProfile;
import com.fcareer.fitengine.model.TeamSetup;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Component
public class SetupModifier {

    private static final int HIGH_LINE = 65;
    private static final int AGGRESSIVE_LINE = 85;
    private static final int LOW_BLOCK = 35;
    private static final int PACE_HIGH_LINE = 66;
    private static final int PACE_AGGRESSIVE = 72;
    private static final int AIR_DUELS = 68;
    private static final int DEFENSIVE_READING = 65;
    private static final int BUILD_UP_PASSING = 66;
    private static final int GOALKEEPER_KICKING = 65;
    private static final int COUNTER_PACE = 76;
    private static final double PACE_WEIGHT = 0.5;
    private static final double AIR_WEIGHT = 0.4;
    private static final double READING_WEIGHT = 0.3;
    private static final double PASSING_WEIGHT = 0.4;
    private static final double COUNTER_WEIGHT = 0.4;
    private static final double MAX_PENALTY = 6.0;
    private static final double MAX_BONUS = 2.0;
    private static final String CENTRE_BACK = "CB";
    private static final String GOALKEEPER = "GK";
    private static final Set<String> BUILD_UP_GROUPS = Set.of("CB", "CDM", "FB");
    private static final Set<String> RUNNER_GROUPS = Set.of("ST", "W", "WM", "CAM");

    /** Takım ayarının (hat yüksekliği, oyun kurulumu) oyuncuya özgü puan etkisi; ayar yoksa 0. Negatif = ayara uymuyor. */
    public double delta(FitPlayer player, IntentProfile slot) {
        TeamSetup setup = slot.getSetup();
        if (Objects.isNull(setup) || Objects.isNull(slot.getSlotGroup())) {
            return 0;
        }
        String group = slot.getSlotGroup();
        double total = 0;
        if (CENTRE_BACK.equals(group) && Objects.nonNull(setup.getDepth())) {
            total += lineDelta(player, setup.getDepth());
        }
        String buildUp = Objects.isNull(setup.getBuildUp()) ? "" : setup.getBuildUp().toLowerCase(Locale.ROOT);
        if (buildUp.startsWith("short")) {
            total += GOALKEEPER.equals(group)
                    ? clamp((player.attr(Attr.GK_KICKING) - GOALKEEPER_KICKING) * PASSING_WEIGHT)
                    : BUILD_UP_GROUPS.contains(group) ? clamp((passing(player) - BUILD_UP_PASSING) * PASSING_WEIGHT) : 0;
        } else if (buildUp.startsWith("counter") && RUNNER_GROUPS.contains(group)) {
            total += clamp((pace(player) - COUNTER_PACE) * COUNTER_WEIGHT);
        }
        return total;
    }

    private double lineDelta(FitPlayer player, int depth) {
        if (depth >= HIGH_LINE) {
            int target = depth >= AGGRESSIVE_LINE ? PACE_AGGRESSIVE : PACE_HIGH_LINE;
            return clamp((pace(player) - target) * PACE_WEIGHT);
        }
        if (depth <= LOW_BLOCK) {
            return clamp((air(player) - AIR_DUELS) * AIR_WEIGHT);
        }
        return clamp((player.attr(Attr.DEF_AWARENESS) - DEFENSIVE_READING) * READING_WEIGHT);
    }

    private static double clamp(double value) {
        return Math.max(-MAX_PENALTY, Math.min(MAX_BONUS, value));
    }

    private static int pace(FitPlayer p) {
        return (p.attr(Attr.ACCELERATION) + p.attr(Attr.SPRINT_SPEED)) / 2;
    }

    private static int air(FitPlayer p) {
        return (p.attr(Attr.HEADING) + p.attr(Attr.JUMPING) + p.attr(Attr.STRENGTH)) / 3;
    }

    private static int passing(FitPlayer p) {
        return (p.attr(Attr.SHORT_PASSING) + p.attr(Attr.BALL_CONTROL)) / 2;
    }
}
