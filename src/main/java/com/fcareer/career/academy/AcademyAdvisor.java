package com.fcareer.career.academy;

import com.fcareer.advisor.model.response.AdvisorResponses.PlayerPositionAdvice;
import com.fcareer.advisor.service.PositionAdviceService;
import com.fcareer.career.desk.SpoilerPolicy;
import com.fcareer.career.service.CareerService;
import com.fcareer.catalog.service.PlayerService;
import com.fcareer.development.plan.DevelopmentPlans;
import com.fcareer.development.plan.DevelopmentPlans.Plan;
import com.fcareer.development.plan.PlanProjector;
import com.fcareer.development.recommend.DevelopmentRecommender;
import com.fcareer.development.recommend.DevelopmentRecommender.PlayerRecommendation;
import com.fcareer.fitengine.configuration.FitEngineProperties;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.FitRole;
import com.fcareer.fitengine.service.RoleFitRouter;
import com.fcareer.roles.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ACADEMY-b madde 4: altyapı satırı için FIT-3 en iyi mevki (taraf gerekçesiyle), gelişim önericisinin planı ve "A takıma al" önerisi. Hepsi tahmin.
 * "A takıma al": gencin önerilen (yoksa Dengeli) planla 1 sezon sonraki Güç'ü, ilgili taktik slotunun yedeğini geçiyorsa (ya da yedek yoksa) öneri çıkar.
 * Yedek: o mevkiyi listeleyen A takım oyuncularının bugünkü Güç sırasında taktikteki slot sayısından sonraki oyuncu. Uygulama oyunda yapılır.
 */
@Component
@RequiredArgsConstructor
public class AcademyAdvisor {

    private static final int PROMOTE_SEASONS = 1;

    private final CareerService careerService;
    private final PlayerService playerService;
    private final PositionAdviceService positionAdvice;
    private final DevelopmentRecommender recommender;
    private final DevelopmentPlans plans;
    private final PlanProjector projector;
    private final RoleService roleService;
    private final RoleFitRouter roleFit;
    private final FitEngineProperties fitProperties;
    private final SpoilerPolicy spoilerPolicy;

    public record Backup(String name, double strength) {
    }

    public record PlanAdvice(String name, String text, boolean balanced) {
    }

    public record Promote(String position, double strength1, String backupName, Double backupStrength, String text) {
    }

    public record Advice(String bestPosition, String sideReason, PlanAdvice plan, Promote promote) {
    }

    /** Oyuncu kimliği → öneri. Sahiplik çağıranda (AcademyService.requireOwned) ve squadPlayerIds'te doğrulanır. */
    public Map<Long, Advice> advise(Long userId, Long careerId, List<Long> academyIds) {
        if (academyIds.isEmpty()) {
            return Map.of();
        }
        CareerService.CareerSquad squad = careerService.squadPlayerIds(userId, careerId);
        boolean ranged = spoilerPolicy.ranged(careerId);
        Function<FitPlayer, FitPlayer> banded = (p) -> ranged && Objects.nonNull(p.getPotential()) ? p.toBuilder().potential(SpoilerPolicy.low(p.getPotential())).build() : p;
        List<FitPlayer> squadPlayers = playerService.fitPlayers(squad.playerIds(), squad.gameVersionId(), careerId).stream().map(banded).toList();
        List<FitPlayer> academy = playerService.fitPlayers(academyIds, squad.gameVersionId(), careerId).stream().map(banded).toList();
        PositionAdviceService.TeamContext team = positionAdvice.teamContext(userId, careerId, squadPlayers);
        Map<Long, PlayerPositionAdvice> positions = positionAdvice.advise(academy, team).stream()
                .collect(Collectors.toMap(PlayerPositionAdvice::getPlayerId, Function.identity(), (a, b) -> a));
        Map<Long, PlayerRecommendation> recommendations = recommender.recommendFor(userId, careerId, academy).stream()
                .collect(Collectors.toMap(PlayerRecommendation::playerId, Function.identity(), (a, b) -> a));
        List<String> slotPositions = positionAdvice.tacticSlotPositions(userId, careerId);
        Map<String, Backup> backups = new HashMap<>();
        Map<Long, Advice> out = new HashMap<>();
        for (FitPlayer player : academy) {
            PlayerPositionAdvice pos = positions.get(player.getId());
            PlayerRecommendation rec = recommendations.get(player.getId());
            PlanAdvice plan = Objects.isNull(rec) ? null
                    : new PlanAdvice(rec.balanced() ? plans.general().name() : rec.recommended().planName(), rec.text(), rec.balanced());
            Plan grow = Objects.isNull(rec) || rec.balanced() ? plans.general() : plans.byKey(rec.recommended().planKey()).orElse(plans.general());
            Promote promote = target(player, pos, slotPositions).map((target) -> {
                double strength1 = best(projector.project(player, grow, PROMOTE_SEASONS).grown(), target);
                int slots = (int) Math.max(1, slotPositions.stream().filter(target::equals).count());
                Backup backup = backups.computeIfAbsent(target + "#" + slots, (k) -> backup(squadAt(squadPlayers, target), slots));
                return promotes(strength1, backup) ? new Promote(target, round1(strength1), Objects.isNull(backup) ? null : backup.name(),
                        Objects.isNull(backup) ? null : round1(backup.strength()), promoteText(player.getName(), target, strength1, backup)) : null;
            }).orElse(null);
            out.put(player.getId(), new Advice(Objects.isNull(pos) ? null : pos.getBestOverallPosition(), Objects.isNull(pos) ? null : pos.getSideReason(), plan, promote));
        }
        return out;
    }

    /** İlgili slot: FIT-3 en iyi mevki taktikteyse o; değilse oyuncunun listelediği mevkilerden taktikte olan ve bugün en güçlü olduğu. */
    private Optional<String> target(FitPlayer player, PlayerPositionAdvice advice, List<String> slotPositions) {
        boolean noTactic = slotPositions.isEmpty();
        String best = Objects.isNull(advice) ? null : advice.getBestOverallPosition();
        if (Objects.nonNull(best) && (noTactic || slotPositions.contains(best))) {
            return Optional.of(best);
        }
        return player.getPositions().stream().filter((p) -> noTactic || slotPositions.contains(p)).max(Comparator.comparingDouble((p) -> best(player, p)));
    }

    private List<Backup> squadAt(List<FitPlayer> squad, String position) {
        return squad.stream().filter((p) -> p.getPositions().contains(position)).map((p) -> new Backup(p.getName(), best(p, position))).toList();
    }

    /** Mevkide, öneri dışı roller hariç en iyi rol Güç'ü. */
    private double best(FitPlayer player, String position) {
        return roleService.fitRolesById().values().stream().filter((r) -> r.getPositions().contains(position) && !fitProperties.recommendationExcluded(r))
                .mapToDouble((FitRole r) -> roleFit.forCareer(player, r, null, position).getScore()).max().orElse(0);
    }

    /** Güç sırasında slot sayısından sonraki oyuncu (tek slotta 2., iki slotta 3.); yoksa null. */
    public static Backup backup(List<Backup> squadAtPosition, int slots) {
        List<Backup> sorted = squadAtPosition.stream().sorted(Comparator.comparingDouble(Backup::strength).reversed()).toList();
        return sorted.size() > slots ? sorted.get(slots) : null;
    }

    public static boolean promotes(double strength1, Backup backup) {
        return Objects.isNull(backup) || strength1 > backup.strength();
    }

    public static String promoteText(String player, String position, double strength1, Backup backup) {
        String tail = " Bu bir tahmin; A takıma alma oyunda yapılır.";
        if (Objects.isNull(backup)) {
            return String.format(Locale.ROOT, "%s · %s için yedek yok; 1 sezon sonra tahmini gücü %d.%s", player, position, Math.round(strength1), tail);
        }
        return String.format(Locale.ROOT, "%s · 1 sezon sonra tahmini %s gücü %d, yedek %s %d.%s", player, position, Math.round(strength1), backup.name(),
                Math.round(backup.strength()), tail);
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
