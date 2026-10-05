package com.fcareer.advisor.service;

import com.fcareer.advisor.configuration.RecommenderProperties;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.tactics.model.FormationDefinition;
import com.fcareer.tactics.model.FormationSlot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FeasibilityService {

    private static final Set<String> WIDE = Set.of("LW", "RW", "LM", "RM");
    private static final Set<String> MID = Set.of("CM", "CDM", "CAM");
    private static final int CORE_SIZE = 14;
    private static final int MID_SAMPLE = 6;
    private static final int PAIR = 2;
    private static final int MIN_CENTRE_BACKS_FOR_BACK_THREE = 3;

    private final RecommenderProperties properties;

    public record Feasibility(double multiplier, List<String> notes) {
    }

    public static class SquadProfile {
        private final Map<Long, Map<String, Double>> scores;
        private final double avgStamina;
        private final double avgPace;
        private final double midTech;
        private final double cbPace;
        private final double fullbackCrossing;
        private final double aerial;
        private final double wideStamina;

        SquadProfile(List<FitPlayer> players, Map<Long, Map<String, Double>> scores) {
            this.scores = scores;
            List<FitPlayer> outfield = players.stream().filter(p -> !p.getPositions().contains("GK"))
                    .sorted(Comparator.comparingInt(FitPlayer::getOverall).reversed()).toList();
            List<FitPlayer> core = outfield.stream().limit(CORE_SIZE).toList();
            avgStamina = average(core, p -> p.attr(Attr.STAMINA));
            avgPace = average(core, p -> pace(p));
            midTech = average(top(outfield, MID_SAMPLE, MID), p -> (p.attr(Attr.VISION) + p.attr(Attr.SHORT_PASSING)
                    + p.attr(Attr.COMPOSURE) + p.attr(Attr.BALL_CONTROL)) / 4.0);
            cbPace = average(top(outfield, PAIR, Set.of("CB")), p -> pace(p));
            fullbackCrossing = average(top(outfield, PAIR, Set.of("LB", "RB")), p -> p.attr(Attr.CROSSING));
            aerial = average(top(outfield, PAIR, Set.of("ST")), p -> (p.attr(Attr.HEADING) + p.attr(Attr.JUMPING)) / 2.0);
            wideStamina = average(top(outfield, PAIR, WIDE), p -> p.attr(Attr.STAMINA));
        }

        private static double pace(FitPlayer p) {
            return (p.attr(Attr.ACCELERATION) + p.attr(Attr.SPRINT_SPEED)) / 2.0;
        }

        private double best(FitPlayer player, Set<String> positions) {
            Map<String, Double> byPosition = scores.getOrDefault(player.getId(), Map.of());
            return positions.stream().mapToDouble(p -> byPosition.getOrDefault(p, 0.0)).max().orElse(0);
        }

        private List<FitPlayer> top(List<FitPlayer> players, int limit, Set<String> positions) {
            return players.stream().sorted(Comparator.comparingDouble((FitPlayer p) -> best(p, positions)).reversed()).limit(limit).toList();
        }

        private static double average(List<FitPlayer> players, java.util.function.ToDoubleFunction<FitPlayer> value) {
            return players.stream().mapToDouble(value).average().orElse(0);
        }

        long usable(List<FitPlayer> players, Set<String> positions, double min) {
            return players.stream().filter(p -> best(p, positions) >= min).count();
        }
    }

    public SquadProfile profile(List<FitPlayer> players, Map<Long, Map<String, Double>> scores) {
        return new SquadProfile(players, scores);
    }

    public Feasibility evaluate(List<FitPlayer> players, SquadProfile profile, FormationDefinition formation, String presetId) {
        List<String> notes = new ArrayList<>();
        double penalty = formationPenalty(players, profile, formation, notes);
        penalty += presetPenalty(players, profile, formation, presetId, notes);
        double multiplier = Math.max(properties.getMinFeasibility(), Math.min(1.0, 1.0 - penalty));
        return new Feasibility(multiplier, notes);
    }

    public Feasibility formationOnly(List<FitPlayer> players, SquadProfile profile, FormationDefinition formation) {
        List<String> notes = new ArrayList<>();
        double penalty = formationPenalty(players, profile, formation, notes);
        return new Feasibility(Math.max(properties.getMinFeasibility(), Math.min(1.0, 1.0 - penalty)), notes);
    }

    private double formationPenalty(List<FitPlayer> players, SquadProfile profile, FormationDefinition formation, List<String> notes) {
        double penalty = 0;
        long strikerSlots = count(formation, Set.of("ST"));
        long camSlots = count(formation, Set.of("CAM"));
        long wideSlots = count(formation, WIDE);
        long cbSlots = count(formation, Set.of("CB"));
        double min = properties.getUsableMin();

        long strikers = profile.usable(players, Set.of("ST"), min);
        if (strikerSlots >= 2 && strikers < strikerSlots) {
            long missing = strikerSlots - strikers;
            penalty += missing * properties.getMissingStriker();
            notes.add(String.format(Locale.ROOT, "%s için %d forvet gerekir, kadroda kullanılabilir %d forvet var.", formation.id(), strikerSlots, strikers));
        }
        if (strikerSlots >= 2 && camSlots == 1 && profile.usable(players, Set.of("CAM"), properties.getCreativeCamMin()) < 1) {
            penalty += properties.getMissingCreativeCam();
            notes.add(formation.id() + " için yaratıcı bir 10 numara gerekir; kadroda yok.");
        }
        long wide = profile.usable(players, WIDE, min);
        if (strikerSlots == 1 && wideSlots >= 2 && wide < wideSlots) {
            penalty += (wideSlots - wide) * properties.getMissingWinger();
            notes.add(String.format(Locale.ROOT, "%s için %d kanat gerekir, kadroda kullanılabilir %d kanat var.", formation.id(), wideSlots, wide));
        }
        long centreBacks = profile.usable(players, Set.of("CB"), min);
        if (cbSlots > 0 && centreBacks < cbSlots) {
            penalty += (cbSlots - centreBacks) * properties.getMissingCentreBack();
            notes.add(String.format(Locale.ROOT, "%s için %d stoper gerekir, kadroda kullanılabilir %d stoper var.", formation.id(), cbSlots, centreBacks));
        }
        if (cbSlots >= MIN_CENTRE_BACKS_FOR_BACK_THREE && profile.wideStamina < properties.getStaminaHigh()) {
            penalty += properties.getLowStaminaWideBack();
            notes.add("Üçlü stoper şekli geniş oyuncuların kondisyonunu zorlar; kadronun kanat kondisyonu düşük.");
        }
        return penalty;
    }

    private double presetPenalty(List<FitPlayer> players, SquadProfile profile, FormationDefinition formation, String presetId,
                                 List<String> notes) {
        if (presetId == null) {
            return 0;
        }
        RecommenderProperties p = properties;
        double penalty = 0;
        switch (presetId) {
            case "gegenpress" -> {
                penalty += below(profile.avgStamina, p.getStaminaHigh(), p.getGegenpressStamina(), "Gegenpress için yüksek kondisyon gerekir (kadro ortalaması %.0f).", notes);
                penalty += below(profile.avgPace, p.getPaceHigh(), p.getGegenpressPace(), "Gegenpress için hız gerekir (kadro ortalaması %.0f).", notes);
                penalty += below(profile.cbPace, p.getCbPaceMin(), p.getHighLineSlowCb(), "Yüksek hat için hızlı stoper gerekir (stoper hızı %.0f).", notes);
            }
            case "tiki_taka" -> penalty += below(profile.midTech, p.getTechHigh(), p.getPossessionTech(),
                    "Tiki-taka için orta sahanın teknik seviyesi yüksek olmalı (ortalama %.0f).", notes);
            case "tiki_gegen_hybrid" -> {
                penalty += below(profile.midTech, p.getTechHigh(), p.getHybridTech(), "Orta saha tekniği yetersiz (ortalama %.0f).", notes);
                penalty += below(profile.avgStamina, p.getStaminaHigh(), p.getHybridStamina(), "Yüksek tempo için kondisyon düşük (%.0f).", notes);
                penalty += below(profile.cbPace, p.getCbPaceMin(), p.getHighLineSlowCb(), "Yüksek hat için stoperler yavaş (hız %.0f).", notes);
            }
            case "possession_control" -> {
                penalty += below(profile.midTech, p.getTechHigh(), p.getHybridTech(), "Top kontrolü için orta saha tekniği düşük (%.0f).", notes);
                if (profile.usable(players, Set.of("CDM"), p.getUsableMin()) == 0) {
                    penalty += p.getMissingPlaymaker();
                    notes.add("Top dağıtan bir derin orta saha yok.");
                }
            }
            case "wing_play", "far_post" -> {
                penalty += below(profile.fullbackCrossing, p.getCrossingHigh(), p.getWingCrossing(), "Orta oyunu için bek ortaları zayıf (Crossing %.0f).", notes);
                penalty += below(profile.aerial, p.getAerialHigh(), p.getWingAerial(), "Ortaların adresi olacak havacı forvet zayıf (%.0f).", notes);
            }
            case "atletico_block" -> {
                if (profile.usable(players, WIDE, p.getUsableMin()) < 2) {
                    penalty += p.getAtleticoWide();
                    notes.add("Atlético bloğu iki iki-yönlü kanat orta saha ister; yeterli kanat yok.");
                }
                if (profile.usable(players, Set.of("ST"), p.getUsableMin()) < 2) {
                    penalty += p.getAtleticoStrikers();
                    notes.add("Atlético bloğu iki forvet ister; ikinci forvet yok.");
                }
            }
            case "personal_cakal_doktrini" -> {
                penalty += below(profile.fullbackCrossing, p.getCrossingHigh(), p.getPersonalCrossing(), "Çakal Doktrini bindiren, ortası iyi bek ister (Crossing %.0f).", notes);
                if (profile.usable(players, Set.of("CAM"), p.getCreativeCamMin()) < 1) {
                    penalty += p.getPersonalCreator();
                    notes.add("Çakal Doktrini'nin kalbi dahi bir 10 numaradır; kadroda yok.");
                }
            }
            default -> {
                // park_the_bus ve anatolian_counter düşük gereksinimlidir; kadro kısıtı yok
            }
        }
        return penalty;
    }

    private double below(double actual, double threshold, double penalty, String message, List<String> notes) {
        if (actual < threshold) {
            notes.add(String.format(Locale.ROOT, message, actual));
            return penalty;
        }
        return 0;
    }

    private long count(FormationDefinition formation, Set<String> positions) {
        return formation.slots().stream().map(FormationSlot::position).filter(positions::contains).count();
    }
}
