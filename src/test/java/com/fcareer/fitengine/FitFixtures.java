package com.fcareer.fitengine;

import com.fcareer.common.service.MessageService;
import com.fcareer.fitengine.configuration.FitEngineProperties;
import com.fcareer.fitengine.model.AcceleRate;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.FitRole;
import com.fcareer.fitengine.model.FitTag;
import com.fcareer.fitengine.model.IntentProfile;
import com.fcareer.fitengine.model.Weapon;
import com.fcareer.fitengine.service.HungarianSolver;
import com.fcareer.fitengine.service.IntentFitService;
import com.fcareer.fitengine.service.NlgService;
import com.fcareer.fitengine.service.RoleFitService;
import com.fcareer.fitengine.service.SquadFitService;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class FitFixtures {

    static final FitEngineProperties PROPERTIES = new FitEngineProperties();
    static final NlgService NLG = new NlgService(new MessageService(messageSource()));
    static final RoleFitService ROLE_FIT = new RoleFitService(PROPERTIES, NLG);
    static final IntentFitService INTENT_FIT = new IntentFitService(PROPERTIES, NLG);
    static final HungarianSolver HUNGARIAN = new HungarianSolver();
    static final SquadFitService SQUAD_FIT = new SquadFitService(ROLE_FIT, INTENT_FIT, HUNGARIAN, NLG, PROPERTIES, new com.fcareer.fitengine.service.SetupModifier(), null);

    private FitFixtures() {
    }

    private static ResourceBundleMessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }

    static PlayerBuilder player(long id, String name, String... positions) {
        return new PlayerBuilder(id, name, List.of(positions));
    }

    static Map<Attr, Double> weights(Object... pairs) {
        Map<Attr, Double> weights = new EnumMap<>(Attr.class);
        for (int i = 0; i < pairs.length; i += 2) {
            weights.put((Attr) pairs[i], (Double) pairs[i + 1]);
        }
        return weights;
    }

    static FitRole role(String id, String baseRole, List<String> positions, Map<Attr, Double> weights,
                        List<Weapon> weapons, List<String> priorityPs, Set<AcceleRate> acceleRates) {
        return FitRole.builder()
                .id(id)
                .baseRole(baseRole)
                .name(id)
                .positions(positions)
                .focus("Attack")
                .attrWeights(weights)
                .weapons(weapons)
                .priorityPlaystyles(priorityPs)
                .bonusPlaystyles(List.of())
                .acceleRates(acceleRates)
                .globalFloor(Map.of(Attr.STAMINA, 68, Attr.REACTIONS, 60, Attr.COMPOSURE, 60))
                .movementTags(List.of())
                .build();
    }

    static FitRole insideForward() {
        return role("inside_forward_attack", "inside_forward", List.of("LW", "RW", "LM", "RM"),
                weights(Attr.FINISHING, 0.30, Attr.DRIBBLING, 0.25, Attr.AGILITY, 0.20, Attr.ACCELERATION, 0.25),
                List.of(new Weapon(Attr.FINISHING, 66, 78)),
                List.of("FinesseShot"), Set.of(AcceleRate.EXPLOSIVE));
    }

    static FitRole winger() {
        return role("winger_attack", "winger", List.of("LW", "RW", "LM", "RM"),
                weights(Attr.CROSSING, 0.30, Attr.ACCELERATION, 0.25, Attr.DRIBBLING, 0.25, Attr.SPRINT_SPEED, 0.20),
                List.of(new Weapon(Attr.ACCELERATION, 80, 90)),
                List.of("WhippedPass"), Set.of(AcceleRate.EXPLOSIVE));
    }

    static FitRole classic10() {
        return role("classic_10_attack", "classic_10", List.of("CAM"),
                weights(Attr.VISION, 0.30, Attr.BALL_CONTROL, 0.25, Attr.SHORT_PASSING, 0.25, Attr.COMPOSURE, 0.20),
                List.of(new Weapon(Attr.VISION, 70, 86), new Weapon(Attr.BALL_CONTROL, 70, 85),
                        new Weapon(Attr.COMPOSURE, 68, 84)),
                List.of("IncisivePass"), Set.of(AcceleRate.EXPLOSIVE, AcceleRate.CONTROLLED));
    }

    static FitRole defender() {
        return role("defender_defend", "defender", List.of("CB"),
                weights(Attr.DEF_AWARENESS, 0.35, Attr.STANDING_TACKLE, 0.35, Attr.STRENGTH, 0.30),
                List.of(new Weapon(Attr.DEF_AWARENESS, 64, 82)),
                List.of("Anticipate"), Set.of(AcceleRate.LENGTHY, AcceleRate.CONTROLLED));
    }

    static FitRole fullback() {
        return role("attacking_wingback_attack", "attacking_wingback", List.of("RB", "LB"),
                weights(Attr.CROSSING, 0.35, Attr.STAMINA, 0.35, Attr.SPRINT_SPEED, 0.30),
                List.of(new Weapon(Attr.CROSSING, 64, 82)),
                List.of("WhippedPass"), Set.of(AcceleRate.EXPLOSIVE));
    }

    static FitRole holding() {
        return role("holding_defend", "holding", List.of("CDM"),
                weights(Attr.DEF_AWARENESS, 0.35, Attr.INTERCEPTIONS, 0.35, Attr.STAMINA, 0.30),
                List.of(new Weapon(Attr.INTERCEPTIONS, 66, 82)),
                List.of("Intercept"), Set.of(AcceleRate.CONTROLLED));
    }

    static FitRole striker() {
        return role("advanced_forward_attack", "advanced_forward", List.of("ST"),
                weights(Attr.FINISHING, 0.40, Attr.POSITIONING, 0.35, Attr.COMPOSURE, 0.25),
                List.of(new Weapon(Attr.FINISHING, 66, 84)),
                List.of("FinesseShot"), Set.of(AcceleRate.EXPLOSIVE, AcceleRate.CONTROLLED));
    }

    static FitTag tag(String id, String position, Attr primary, int expectedMin) {
        return FitTag.builder()
                .id(id)
                .position(position)
                .labelTr(id)
                .weaponAttrs(List.of(primary))
                .playstyleWeapons(List.of())
                .expectedMin(expectedMin)
                .conflicts(List.of())
                .nlgTr("Test cümlesi " + id)
                .build();
    }

    static IntentProfile slot(String slotId, String position, String group, FitRole role, FitTag... tags) {
        Map<Attr, Double> boost = new EnumMap<>(Attr.class);
        Arrays.stream(tags).flatMap(t -> t.getWeaponAttrs().stream())
                .forEach(a -> boost.merge(a, PROPERTIES.getIntentWeaponBoost(), Double::sum));
        return IntentProfile.builder()
                .slotId(slotId)
                .slotPosition(position)
                .slotGroup(group)
                .tags(List.of(tags))
                .weaponBoost(boost)
                .resolvedRole(role)
                .build();
    }

    static final class PlayerBuilder {
        private final long id;
        private final String name;
        private final List<String> positions;
        private final Map<Attr, Integer> attrs = new EnumMap<>(Attr.class);
        private final Map<String, Integer> playstyles = new HashMap<>();
        private AcceleRate acceleRate = AcceleRate.CONTROLLED;

        private PlayerBuilder(long id, String name, List<String> positions) {
            this.id = id;
            this.name = name;
            this.positions = positions;
            for (Attr attr : Attr.values()) {
                attrs.put(attr, 70);
            }
        }

        PlayerBuilder attr(Attr attr, int value) {
            attrs.put(attr, value);
            return this;
        }

        PlayerBuilder all(int value) {
            attrs.replaceAll((a, v) -> value);
            return this;
        }

        PlayerBuilder playstyle(String playstyle, int level) {
            playstyles.put(playstyle, level);
            return this;
        }

        PlayerBuilder accelerate(AcceleRate rate) {
            this.acceleRate = rate;
            return this;
        }

        FitPlayer build() {
            return FitPlayer.builder()
                    .id(id)
                    .name(name)
                    .age(24)
                    .overall(75)
                    .positions(positions)
                    .attrs(attrs)
                    .playstyles(playstyles)
                    .acceleRate(acceleRate)
                    .build();
        }
    }
}
