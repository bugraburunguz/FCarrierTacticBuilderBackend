package com.fcareer.catalog.mapper;

import com.fcareer.catalog.entity.PlayerRatingEntity;
import com.fcareer.fitengine.model.Attr;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

@Component
public class RatingAttrAccessor {

    public Map<Attr, Integer> read(PlayerRatingEntity r) {
        Map<Attr, Integer> attrs = new EnumMap<>(Attr.class);
        for (Attr attr : Attr.values()) {
            Short value = get(r, attr);
            attrs.put(attr, Objects.isNull(value) ? 0 : value.intValue());
        }
        return attrs;
    }

    public void write(PlayerRatingEntity r, Map<Attr, Integer> attrs) {
        attrs.forEach((attr, value) -> set(r, attr, value.shortValue()));
    }

    private Short get(PlayerRatingEntity r, Attr attr) {
        return switch (attr) {
            case ACCELERATION -> r.getAcceleration();
            case SPRINT_SPEED -> r.getSprintSpeed();
            case AGILITY -> r.getAgility();
            case BALANCE -> r.getBalance();
            case REACTIONS -> r.getReactions();
            case BALL_CONTROL -> r.getBallControl();
            case DRIBBLING -> r.getDribbling();
            case COMPOSURE -> r.getComposure();
            case VISION -> r.getVision();
            case SHORT_PASSING -> r.getShortPassing();
            case LONG_PASSING -> r.getLongPassing();
            case CROSSING -> r.getCrossing();
            case CURVE -> r.getCurve();
            case FK_ACCURACY -> r.getFkAccuracy();
            case FINISHING -> r.getFinishing();
            case SHOT_POWER -> r.getShotPower();
            case LONG_SHOTS -> r.getLongShots();
            case VOLLEYS -> r.getVolleys();
            case PENALTIES -> r.getPenalties();
            case POSITIONING -> r.getAttPosition();
            case HEADING -> r.getHeading();
            case DEF_AWARENESS -> r.getDefAwareness();
            case STANDING_TACKLE -> r.getStandingTackle();
            case SLIDING_TACKLE -> r.getSlidingTackle();
            case INTERCEPTIONS -> r.getInterceptions();
            case JUMPING -> r.getJumping();
            case STAMINA -> r.getStamina();
            case STRENGTH -> r.getStrength();
            case AGGRESSION -> r.getAggression();
            case GK_DIVING -> r.getGkDiving();
            case GK_HANDLING -> r.getGkHandling();
            case GK_KICKING -> r.getGkKicking();
            case GK_POSITIONING -> r.getGkPositioning();
            case GK_REFLEXES -> r.getGkReflexes();
        };
    }

    private void set(PlayerRatingEntity r, Attr attr, Short v) {
        switch (attr) {
            case ACCELERATION -> r.setAcceleration(v);
            case SPRINT_SPEED -> r.setSprintSpeed(v);
            case AGILITY -> r.setAgility(v);
            case BALANCE -> r.setBalance(v);
            case REACTIONS -> r.setReactions(v);
            case BALL_CONTROL -> r.setBallControl(v);
            case DRIBBLING -> r.setDribbling(v);
            case COMPOSURE -> r.setComposure(v);
            case VISION -> r.setVision(v);
            case SHORT_PASSING -> r.setShortPassing(v);
            case LONG_PASSING -> r.setLongPassing(v);
            case CROSSING -> r.setCrossing(v);
            case CURVE -> r.setCurve(v);
            case FK_ACCURACY -> r.setFkAccuracy(v);
            case FINISHING -> r.setFinishing(v);
            case SHOT_POWER -> r.setShotPower(v);
            case LONG_SHOTS -> r.setLongShots(v);
            case VOLLEYS -> r.setVolleys(v);
            case PENALTIES -> r.setPenalties(v);
            case POSITIONING -> r.setAttPosition(v);
            case HEADING -> r.setHeading(v);
            case DEF_AWARENESS -> r.setDefAwareness(v);
            case STANDING_TACKLE -> r.setStandingTackle(v);
            case SLIDING_TACKLE -> r.setSlidingTackle(v);
            case INTERCEPTIONS -> r.setInterceptions(v);
            case JUMPING -> r.setJumping(v);
            case STAMINA -> r.setStamina(v);
            case STRENGTH -> r.setStrength(v);
            case AGGRESSION -> r.setAggression(v);
            case GK_DIVING -> r.setGkDiving(v);
            case GK_HANDLING -> r.setGkHandling(v);
            case GK_KICKING -> r.setGkKicking(v);
            case GK_POSITIONING -> r.setGkPositioning(v);
            case GK_REFLEXES -> r.setGkReflexes(v);
        }
    }
}
