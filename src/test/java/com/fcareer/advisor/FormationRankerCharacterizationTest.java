package com.fcareer.advisor;

import com.fcareer.IntegrationTest;
import com.fcareer.advisor.service.FeasibilityService;
import com.fcareer.advisor.service.FormationRanker;
import com.fcareer.advisor.service.FormationRanker.RankedFormation;
import com.fcareer.advisor.service.PositionAdviceService;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.tactics.service.FormationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * KARIYER-UX FAZ 1 düzeltme 8: formasyon-only sıralamanın (ücretli taktik önerici + ücretsiz başlangıç önerisi) davranış sabitlemesi.
 * Sentetik, tohumlu iki kadro (dengeli; stopersiz) için ilk üç formasyon ve skor sabittir. Katsayı / rol ağırlığı / uygulanabilirlik değişikliği
 * sıralamayı kaydırırsa bu test kırılır; kayma bilinçliyse beklenenler gerekçesiyle güncellenir. Veritabanı kataloğu kullanılmaz (testler arası değişmez).
 */
@IntegrationTest
class FormationRankerCharacterizationTest {

    private static final Set<Attr> GK = Set.of(Attr.GK_DIVING, Attr.GK_HANDLING, Attr.GK_KICKING, Attr.GK_POSITIONING, Attr.GK_REFLEXES);
    private static final Set<Attr> DEF = Set.of(Attr.DEF_AWARENESS, Attr.STANDING_TACKLE, Attr.SLIDING_TACKLE, Attr.INTERCEPTIONS, Attr.HEADING,
            Attr.JUMPING, Attr.STRENGTH, Attr.AGGRESSION);
    private static final Set<Attr> MID = Set.of(Attr.SHORT_PASSING, Attr.LONG_PASSING, Attr.VISION, Attr.BALL_CONTROL, Attr.STAMINA, Attr.COMPOSURE,
            Attr.INTERCEPTIONS);
    private static final Set<Attr> WIDE = Set.of(Attr.ACCELERATION, Attr.SPRINT_SPEED, Attr.AGILITY, Attr.DRIBBLING, Attr.CROSSING, Attr.CURVE,
            Attr.BALL_CONTROL);
    private static final Set<Attr> ATT = Set.of(Attr.FINISHING, Attr.SHOT_POWER, Attr.POSITIONING, Attr.VOLLEYS, Attr.COMPOSURE, Attr.HEADING,
            Attr.LONG_SHOTS);

    @Autowired
    private FormationRanker ranker;
    @Autowired
    private FeasibilityService feasibilityService;
    @Autowired
    private PositionAdviceService positionAdviceService;
    @Autowired
    private FormationRegistry formationRegistry;

    private static Set<Attr> strengths(String position) {
        return switch (position) {
            case "GK" -> GK;
            case "CB", "LB", "RB" -> DEF;
            case "CDM", "CM", "CAM" -> MID;
            case "LW", "RW", "LM", "RM" -> WIDE;
            default -> ATT;
        };
    }

    private static FitPlayer player(long id, String position) {
        Random random = new Random(id * 7919L);
        Set<Attr> strong = strengths(position);
        Map<Attr, Integer> attrs = new EnumMap<>(Attr.class);
        for (Attr attr : Attr.values()) {
            boolean keeper = GK.contains(attr);
            int base = "GK".equals(position) ? (keeper ? 70 : 35) : (keeper ? 10 : 50);
            attrs.put(attr, base + (strong.contains(attr) ? 18 : 0) + random.nextInt(10));
        }
        List<String> positions = switch (position) {
            case "LB" -> List.of("LB", "LWB");
            case "RB" -> List.of("RB", "RWB");
            case "CDM" -> List.of("CDM", "CM");
            case "CAM" -> List.of("CAM", "CM");
            case "LW" -> List.of("LW", "LM");
            case "RW" -> List.of("RW", "RM");
            default -> List.of(position);
        };
        return FitPlayer.builder().id(id).name("P" + id).age(25).overall(70).positions(positions).attrs(attrs).playstyles(Map.of())
                .potential(75).build();
    }

    private static List<FitPlayer> squad(boolean withCentreBacks) {
        List<String> shape = new ArrayList<>(List.of("GK", "GK", "LB", "LB", "RB", "RB", "CDM", "CDM", "CM", "CM", "CM", "CAM", "CAM",
                "LW", "LW", "RW", "RW", "ST", "ST", "ST"));
        if (withCentreBacks) {
            shape.addAll(List.of("CB", "CB", "CB", "CB"));
        }
        List<FitPlayer> players = new ArrayList<>();
        for (int i = 0; i < shape.size(); i++) {
            players.add(player(1000L + i, shape.get(i)));
        }
        return players;
    }

    private List<RankedFormation> topThree(List<FitPlayer> players) {
        var profile = feasibilityService.profile(players, positionAdviceService.bestScores(players));
        return ranker.rank(players, profile, formationRegistry.findAll()).subList(0, 3);
    }

    private static void expectTop(List<RankedFormation> actual, List<String> ids, List<Double> scores) {
        assertThat(actual.stream().map(r -> r.formation().id()).toList()).isEqualTo(ids);
        for (int i = 0; i < scores.size(); i++) {
            assertThat(actual.get(i).score()).as(ids.get(i)).isCloseTo(scores.get(i), within(0.01));
        }
    }

    /** Sabitlendi 2026-10-22 (refactor sonrası, refactor öncesiyle aynı formül: squadFit × formationOnly). */
    @Test
    void balancedSquadKeepsItsTopThree() {
        expectTop(topThree(squad(true)), List.of("4-3-3 (2 CDM)", "4-2-1-3", "4-3-3"), List.of(61.6636, 61.3909, 61.2727));
    }

    /** Stoper yok: skorlar çöker ama sıralama dört bekli şekillerde kalır (geri üçlü daha da kötü). */
    @Test
    void squadWithoutCentreBacksKeepsItsTopThree() {
        expectTop(topThree(squad(false)), List.of("4-3-3 (CAM)", "4-2-4", "4-3-3 (3 CM)"), List.of(22.7782, 22.7564, 22.7236));
    }
}
