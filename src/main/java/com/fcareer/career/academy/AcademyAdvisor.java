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
import com.fcareer.development.recommend.DevelopmentRecommender.Option;
import com.fcareer.development.recommend.DevelopmentRecommender.PlayerRecommendation;
import com.fcareer.catalog.service.PositionOverallService;
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
    private static final int TEAM_SEASONS = 3;
    private static final String GOALKEEPER = "GK";

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
    private final PositionOverallService positionOverall;

    public record Backup(String name, double strength) {
    }

    /** label: satırdaki tek etiket ("CDM · Holding", "CDM mevki", "Dengeli"); text: gerekçe. */
    public record PlanAdvice(String name, String label, String text, boolean balanced) {
    }

    public record Promote(String position, double strength1, String backupName, Double backupStrength, String text) {
    }

    /** conversionPosition: FIT-3 dönüşüm önerisinin mevkisi ("CM → CDM önerilir"); yoksa null. */
    public record Advice(String bestPosition, String sideReason, String conversionPosition, PlanAdvice plan, Promote promote) {
    }

    /** "Ne yapmalıyım?" plan satırı: hedef mevkide 1 ve 2 sezon sonra tahmini OVR. kind: GENERAL, ROLE, POSITION. */
    public record PlanRow(String label, String kind, String position, int overall1, int overall2) {
    }

    public record WhatToDo(String sentence, List<PlanRow> plans, boolean estimated) {
    }

    public record WhichPosition(String bestPosition, Integer bestOverall, List<String> listedPositions, String conversionText, String sideReason) {
    }

    /** "Takımdaki yeri": hedef slot; ilk 11 (slot sayısı kadar en güçlüden sonuncusu) ve yedek; 1–3 sezon tahmini Güç; kaçıncı sezonda geçer. source: MAIN, AUTO. */
    public record TeamPlace(String position, String source, int slots, String starterName, Double starterStrength, String backupName, Double backupStrength,
                            List<Double> strengths, Integer passBackupSeason, Integer passStarterSeason) {
    }

    public record Detail(WhatToDo advice, WhichPosition position, TeamPlace team) {
    }

    private record Context(List<FitPlayer> squad, List<FitPlayer> targets, Map<Long, PlayerPositionAdvice> positions,
                           Map<Long, PlayerRecommendation> recommendations, List<String> slotPositions) {
    }

    /** Spoiler: SCOUT_RANGE'de bütün tahminler bandın alt sınırıyla (gerçek potansiyel geri hesaplanamaz). */
    private Context context(Long userId, Long careerId, List<Long> ids) {
        CareerService.CareerSquad squad = careerService.squadPlayerIds(userId, careerId);
        boolean ranged = spoilerPolicy.ranged(careerId);
        Function<FitPlayer, FitPlayer> banded = (p) -> ranged && Objects.nonNull(p.getPotential()) ? p.toBuilder().potential(SpoilerPolicy.low(p.getPotential())).build() : p;
        List<FitPlayer> squadPlayers = playerService.fitPlayers(squad.playerIds(), squad.gameVersionId(), careerId).stream().map(banded).toList();
        List<FitPlayer> targets = playerService.fitPlayers(ids, squad.gameVersionId(), careerId).stream().map(banded).toList();
        PositionAdviceService.TeamContext team = positionAdvice.teamContext(userId, careerId, squadPlayers);
        Map<Long, PlayerPositionAdvice> positions = positionAdvice.advise(targets, team).stream()
                .collect(Collectors.toMap(PlayerPositionAdvice::getPlayerId, Function.identity(), (a, b) -> a));
        Map<Long, PlayerRecommendation> recommendations = recommender.recommendFor(userId, careerId, targets).stream()
                .collect(Collectors.toMap(PlayerRecommendation::playerId, Function.identity(), (a, b) -> a));
        return new Context(squadPlayers, targets, positions, recommendations, positionAdvice.tacticSlotPositions(userId, careerId));
    }

    /**
     * Oyuncu kimliği → satır önerisi. promotable: altyapı oyuncusu ("A takıma al" yalnız onlara). Sahiplik çağıranda (requireOwned) ve
     * squadPlayerIds'te doğrulanır; kimlikler çağıranın bu kariyerden okuduğu oyunculardır.
     */
    public Map<Long, Advice> advise(Long userId, Long careerId, List<Long> ids, boolean promotable) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Context ctx = context(userId, careerId, ids);
        Map<String, Backup> backups = new HashMap<>();
        Map<Long, Advice> out = new HashMap<>();
        for (FitPlayer player : ctx.targets()) {
            PlayerPositionAdvice pos = ctx.positions().get(player.getId());
            PlayerRecommendation rec = ctx.recommendations().get(player.getId());
            Plan grow = growPlan(rec);
            Promote promote = !promotable ? null : target(player, pos, ctx.slotPositions()).map((target) -> {
                double strength1 = best(projector.project(player, grow, PROMOTE_SEASONS).grown(), target);
                int slots = slotCount(ctx.slotPositions(), target);
                Backup backup = backups.computeIfAbsent(target + "#" + slots, (k) -> backup(squadAt(ctx.squad(), target, player.getId()), slots));
                return promotes(strength1, backup) ? new Promote(target, round1(strength1), Objects.isNull(backup) ? null : backup.name(),
                        Objects.isNull(backup) ? null : round1(backup.strength()), promoteText(player.getName(), target, strength1, backup)) : null;
            }).orElse(null);
            out.put(player.getId(), new Advice(Objects.isNull(pos) ? null : pos.getBestOverallPosition(), Objects.isNull(pos) ? null : pos.getSideReason(),
                    Objects.isNull(pos) || Objects.isNull(pos.getConversion()) ? null : pos.getConversion().getPosition(), planAdvice(rec), promote));
        }
        return out;
    }

    /** ACADEMY-b paneli: Ne yapmalıyım? · Hangi mevkide? · Takımdaki yeri. Hepsi tahmin. */
    public Optional<Detail> detail(Long userId, Long careerId, Long playerId) {
        Context ctx = context(userId, careerId, List.of(playerId));
        if (ctx.targets().isEmpty()) {
            return Optional.empty();
        }
        FitPlayer player = ctx.targets().get(0);
        PlayerPositionAdvice pos = ctx.positions().get(playerId);
        PlayerRecommendation rec = ctx.recommendations().get(playerId);
        String bestPosition = Objects.isNull(pos) || Objects.isNull(pos.getBestOverallPosition()) ? player.getPositions().get(0) : pos.getBestOverallPosition();

        Optional<Option> rolePlan = Objects.isNull(rec) ? Optional.empty()
                : rec.options().stream().filter((o) -> "ROLE".equals(o.kind())).max(Comparator.comparingDouble(Option::strength2));
        String planPosition = Objects.nonNull(rec) && Objects.nonNull(rec.recommended()) ? rec.recommended().position() : rolePlan.map(Option::position).orElse(bestPosition);
        List<PlanRow> rows = new java.util.ArrayList<>();
        rows.add(planRow(player, plans.general(), plans.general().name(), "GENERAL", planPosition));
        rolePlan.flatMap((o) -> plans.byKey(o.planKey()).map((p) -> planRow(player, p, o.position() + " · " + o.roleName(), "ROLE", o.position()))).ifPresent(rows::add);
        if (Objects.nonNull(pos) && Objects.nonNull(pos.getConversion())) {
            String target = pos.getConversion().getPosition();
            plans.positionPlan(target).map((p) -> planRow(player, p, target + " mevki", "POSITION", target)).ifPresent(rows::add);
        }
        WhatToDo whatToDo = new WhatToDo(sentence(rec, rows), rows, true);
        WhichPosition which = new WhichPosition(bestPosition, Objects.isNull(pos) ? null : pos.getBestOverall(), player.getPositions(),
                Objects.isNull(pos) || Objects.isNull(pos.getConversion()) ? null : pos.getConversion().getText(), Objects.isNull(pos) ? null : pos.getSideReason());

        String target = target(player, pos, ctx.slotPositions()).orElse(bestPosition);
        int slots = slotCount(ctx.slotPositions(), target);
        List<Backup> ranked = squadAt(ctx.squad(), target, playerId).stream().sorted(Comparator.comparingDouble(Backup::strength).reversed()).toList();
        Backup starter = ranked.size() >= slots ? ranked.get(slots - 1) : null;
        Backup backup = backup(ranked, slots);
        Plan grow = growPlan(rec);
        List<Double> strengths = java.util.stream.IntStream.rangeClosed(1, TEAM_SEASONS)
                .mapToObj((s) -> round1(best(projector.project(player, grow, s).grown(), target))).toList();
        TeamPlace team = new TeamPlace(target, positionAdvice.hasMainTactic(userId, careerId) ? "MAIN" : "AUTO", slots,
                Objects.isNull(starter) ? null : starter.name(), Objects.isNull(starter) ? null : round1(starter.strength()),
                Objects.isNull(backup) ? null : backup.name(), Objects.isNull(backup) ? null : round1(backup.strength()),
                strengths, seasonToPass(strengths, Objects.isNull(backup) ? null : backup.strength()),
                seasonToPass(strengths, Objects.isNull(starter) ? null : starter.strength()));
        return Optional.of(new Detail(whatToDo, which, team));
    }

    private Plan growPlan(PlayerRecommendation rec) {
        return Objects.isNull(rec) || rec.balanced() ? plans.general() : plans.byKey(rec.recommended().planKey()).orElse(plans.general());
    }

    /** Satır etiketi oyunun dilinde: "CDM · Holding", "CDM mevki" ya da "Dengeli". */
    private PlanAdvice planAdvice(PlayerRecommendation rec) {
        if (Objects.isNull(rec)) {
            return null;
        }
        if (rec.balanced() || Objects.isNull(rec.recommended())) {
            return new PlanAdvice(plans.general().name(), plans.general().name(), rec.text(), true);
        }
        Option o = rec.recommended();
        String label = "POSITION".equals(o.kind()) ? o.position() + " mevki" : o.position() + " · " + o.roleName();
        return new PlanAdvice(o.planName(), label, rec.text(), false);
    }

    private PlanRow planRow(FitPlayer player, Plan plan, String label, String kind, String position) {
        return new PlanRow(label, kind, position, overallAt(player, plan, 1, position), overallAt(player, plan, 2, position));
    }

    /** Planla `seasons` sonra hedef mevkideki tahmini OVR (mevki formülü; bilinmezse genel OVR). */
    private int overallAt(FitPlayer player, Plan plan, int seasons, String position) {
        PlanProjector.PlanProjection p = projector.project(player, plan, seasons);
        FitPlayer grown = p.grown();
        Integer at = positionOverall.byPosition(grown.getAttrs(), grown.getPositions().contains(GOALKEEPER), grown.getPositions().get(0), p.overall()).get(position);
        return Objects.isNull(at) ? p.overall() : at;
    }

    /** Tek cümle: önerilen plan ve 2 sezon sonraki OVR farkı; plan yoksa neden Dengeli. */
    private static String sentence(PlayerRecommendation rec, List<PlanRow> rows) {
        if (Objects.isNull(rec)) {
            return "Gelişim payı az: Dengeli plan yeterli.";
        }
        if (Objects.nonNull(rec.recommended()) && !rec.balanced()) {
            Option o = rec.recommended();
            PlanRow chosen = rows.stream().filter((r) -> r.position().equals(o.position()) && !"GENERAL".equals(r.kind())).findFirst().orElse(rows.get(0));
            String label = "POSITION".equals(o.kind()) ? o.position() + " mevki" : o.position() + " · " + o.roleName();
            return String.format(Locale.ROOT, "%s planını seç: 2 sezonda %s OVR %d (Dengeli %d).", label, chosen.position(), chosen.overall2(), rows.get(0).overall2());
        }
        return "NO_CLEAR_TARGET".equals(rec.reason()) ? "Dengeli planla devam et: henüz net bir mevkisi yok, esnek gelişsin."
                : "Dengeli plan yeterli: özel plan belirgin fark getirmiyor.";
    }

    private static int slotCount(List<String> slotPositions, String position) {
        return (int) Math.max(1, slotPositions.stream().filter(position::equals).count());
    }

    /** Durum (verified:false): READY "A takıma al" önerisi var; WAITING altyapıda ≥ eşik ay ve son içe aktarmalarda OVR artışı yok; GROWING diğerleri. */
    public static String status(boolean promote, Integer monthsInSquad, List<Integer> recentOveralls, int waitingMonths) {
        if (promote) {
            return "READY";
        }
        boolean long_ = Objects.nonNull(monthsInSquad) && monthsInSquad >= waitingMonths;
        boolean known = Objects.nonNull(recentOveralls) && recentOveralls.size() >= 2 && recentOveralls.stream().allMatch(Objects::nonNull);
        boolean stalled = known && recentOveralls.get(0) <= recentOveralls.get(recentOveralls.size() - 1);
        return long_ && stalled ? "WAITING" : "GROWING";
    }

    /** İlk geçtiği sezon (1'den); eşik yoksa 1 (boş yer); hiç geçmezse null. */
    public static Integer seasonToPass(List<Double> strengths, Double threshold) {
        if (Objects.isNull(threshold)) {
            return 1;
        }
        for (int i = 0; i < strengths.size(); i++) {
            if (strengths.get(i) > threshold) {
                return i + 1;
            }
        }
        return null;
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

    /** Mevkiyi listeleyen kadro oyuncuları (oyuncunun kendisi hariç: kadrodaki genç kendi yedeği sayılmaz). */
    private List<Backup> squadAt(List<FitPlayer> squad, String position, Long exclude) {
        return squad.stream().filter((p) -> p.getPositions().contains(position) && !p.getId().equals(exclude))
                .map((p) -> new Backup(p.getName(), best(p, position))).toList();
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
