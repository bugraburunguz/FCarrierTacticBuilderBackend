package com.fcareer.mlclient.service;

import com.fcareer.catalog.entity.BasePlayerEntity;
import com.fcareer.catalog.entity.PlayerRatingEntity;
import com.fcareer.catalog.mapper.RatingAttrAccessor;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.mlclient.configuration.MlProperties;
import com.fcareer.mlclient.model.MlPlayerFeatures;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class MlFeatureBuilder {

    // Tanım ml-service/app/features.py ATTRIBUTE_GROUPS ile birebir aynı olmalı (CONFIG/calibrate).
    static final List<Attr> PACE = List.of(Attr.ACCELERATION, Attr.SPRINT_SPEED);
    static final List<Attr> SHOOTING = List.of(Attr.POSITIONING, Attr.FINISHING, Attr.SHOT_POWER, Attr.LONG_SHOTS,
            Attr.VOLLEYS, Attr.PENALTIES);
    static final List<Attr> PASSING = List.of(Attr.VISION, Attr.CROSSING, Attr.FK_ACCURACY, Attr.SHORT_PASSING,
            Attr.LONG_PASSING, Attr.CURVE);
    static final List<Attr> DRIBBLING = List.of(Attr.AGILITY, Attr.BALANCE, Attr.REACTIONS, Attr.BALL_CONTROL,
            Attr.DRIBBLING, Attr.COMPOSURE);
    static final List<Attr> DEFENDING = List.of(Attr.INTERCEPTIONS, Attr.HEADING, Attr.DEF_AWARENESS,
            Attr.STANDING_TACKLE, Attr.SLIDING_TACKLE);
    static final List<Attr> PHYSICAL = List.of(Attr.JUMPING, Attr.STAMINA, Attr.STRENGTH, Attr.AGGRESSION);

    private static final String GK = "GK";
    private static final Map<String, String> POSITION_GROUPS = Map.ofEntries(
            Map.entry("GK", "GK"),
            Map.entry("CB", "DEF"), Map.entry("LB", "DEF"), Map.entry("RB", "DEF"), Map.entry("LWB", "DEF"), Map.entry("RWB", "DEF"),
            Map.entry("CDM", "MID"), Map.entry("CM", "MID"), Map.entry("CAM", "MID"), Map.entry("LM", "MID"), Map.entry("RM", "MID"),
            Map.entry("LW", "ATT"), Map.entry("RW", "ATT"), Map.entry("ST", "ATT"), Map.entry("CF", "ATT"),
            Map.entry("LF", "ATT"), Map.entry("RF", "ATT"));

    private final RatingAttrAccessor ratingAttrAccessor;
    private final MlProperties properties;

    public MlPlayerFeatures build(BasePlayerEntity base, PlayerRatingEntity rating, Integer leagueTier) {
        Map<Attr, Integer> attrs = ratingAttrAccessor.read(rating);
        String group = rating.getPositions().isEmpty() ? null : POSITION_GROUPS.get(rating.getPositions().get(0));
        return MlPlayerFeatures.builder()
                .playerId(base.getId())
                .age(age(base))
                .overall(rating.getOverall().intValue())
                .positionGroup(group)
                .leagueTier(leagueTier)
                .intlReputation(Objects.isNull(rating.getIntlReputation()) ? null : rating.getIntlReputation().intValue())
                .pace(mean(attrs, PACE))
                .shooting(mean(attrs, SHOOTING))
                .passing(mean(attrs, PASSING))
                .dribbling(mean(attrs, DRIBBLING))
                .defending(mean(attrs, DEFENDING))
                .physical(mean(attrs, PHYSICAL))
                .weakFoot(Objects.isNull(rating.getWeakFoot()) ? null : rating.getWeakFoot().intValue())
                .skillMoves(Objects.isNull(rating.getSkillMoves()) ? null : rating.getSkillMoves().intValue())
                .goalkeeper(GK.equals(group))
                .build();
    }

    private Integer age(BasePlayerEntity base) {
        if (Objects.isNull(base.getDob())) {
            return null;
        }
        LocalDate reference = Objects.isNull(properties.getAgeReferenceDate()) ? LocalDate.now() : properties.getAgeReferenceDate();
        return Period.between(base.getDob(), reference).getYears();
    }

    static double mean(Map<Attr, Integer> attrs, List<Attr> members) {
        return members.stream().mapToInt(a -> attrs.getOrDefault(a, 0)).average().orElse(0);
    }
}
