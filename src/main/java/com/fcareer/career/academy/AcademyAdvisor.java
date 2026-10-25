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
import com.fcareer.development.service.GrowthProjector;
import com.fcareer.catalog.service.PositionOverallService;
import com.fcareer.fitengine.configuration.FitEngineProperties;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.FitRole;
import com.fcareer.fitengine.service.RoleFitRouter;
import com.fcareer.roles.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
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
    private static final double DAYS_PER_SEASON = 365.25;
    private static final java.time.format.DateTimeFormatter DATE = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy");
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
    private final AcademyProperties academyProperties;
    private final GrowthProjector.Config growthConfig;

    public record Backup(String name, double strength) {
    }

    /** GROWTH-CAL: A takıma alma kararı — 16 yaş altı bilgi notu (INFO), yaş tamam ve yedeği geçiyor (PROMOTE), diğerleri yok (NONE). */
    public enum PromotionDecision { PROMOTE, INFO, NONE }

    /**
     * 16 yaş altı altyapı oyuncusu: "16 yaşında A takıma alınabilir · tarih". Kıyas o tarihteki (aynı ufuk) tahminle: gencin ve yedeğin tahmini Güç'ü.
     */
    public record PromoteInfo(LocalDate eligibleOn, String text, String position, Double strength, String backupName, Double backupStrength) {
    }

    /** GROWTH-CAL: gencin ufku — kaç sezon altyapıda sayılır ve kıyas kaç sezon sonra (16 yaş altı: 16. doğum gününe kadar). */
    public record Timing(LocalDate gameDate, Map<Long, LocalDate> eligibleOn) {
        public static final Timing NONE = new Timing(null, Map.of());
    }

    private record Ranked(FitPlayer player, double strength) {
    }

    /** label: satırdaki tek etiket ("CDM · Holding", "CDM mevki", "Dengeli"); text: gerekçe. */
    public record PlanAdvice(String name, String label, String text, boolean balanced) {
    }

    public record Promote(String position, double strength1, String backupName, Double backupStrength, String text) {
    }

    /** conversionPosition: FIT-3 dönüşüm önerisinin mevkisi ("CM → CDM önerilir"); yoksa null. */
    public record Advice(String bestPosition, String sideReason, String conversionPosition, PlanAdvice plan, Promote promote, PromoteInfo promoteInfo) {
    }

    /** "Ne yapmalıyım?" plan satırı: hedef mevkide 1 ve 2 sezon sonra tahmini OVR. kind: GENERAL, ROLE, POSITION. */
    public record PlanRow(String label, String kind, String position, int overall1, int overall2) {
    }

    /** assumption: tahminin varsayımı, tek satır (GROWTH-CAL: altyapı/A takım kazanç tavanları). */
    public record WhatToDo(String sentence, List<PlanRow> plans, boolean estimated, String assumption) {
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
    private Context context(Long userId, Long careerId, List<Long> ids, int academySeasons) {
        CareerService.CareerSquad squad = careerService.squadPlayerIds(userId, careerId);
        boolean ranged = spoilerPolicy.ranged(careerId);
        Function<FitPlayer, FitPlayer> banded = (p) -> ranged && Objects.nonNull(p.getPotential()) ? p.toBuilder().potential(SpoilerPolicy.low(p.getPotential())).build() : p;
        List<FitPlayer> squadPlayers = playerService.fitPlayers(squad.playerIds(), squad.gameVersionId(), careerId).stream().map(banded).toList();
        List<FitPlayer> targets = playerService.fitPlayers(ids, squad.gameVersionId(), careerId).stream().map(banded).toList();
        PositionAdviceService.TeamContext team = positionAdvice.teamContext(userId, careerId, squadPlayers);
        Map<Long, PlayerPositionAdvice> positions = positionAdvice.advise(targets, team).stream()
                .collect(Collectors.toMap(PlayerPositionAdvice::getPlayerId, Function.identity(), (a, b) -> a));
        Map<Long, PlayerRecommendation> recommendations = recommender.recommendFor(userId, careerId, targets, academySeasons).stream()
                .collect(Collectors.toMap(PlayerRecommendation::playerId, Function.identity(), (a, b) -> a));
        return new Context(squadPlayers, targets, positions, recommendations, positionAdvice.tacticSlotPositions(userId, careerId));
    }

    /**
     * Oyuncu kimliği → satır önerisi. promotable: altyapı oyuncusu ("A takıma al" yalnız onlara). Sahiplik çağıranda (requireOwned) ve
     * squadPlayerIds'te doğrulanır; kimlikler çağıranın bu kariyerden okuduğu oyunculardır.
     */
    public Map<Long, Advice> advise(Long userId, Long careerId, List<Long> ids, boolean promotable) {
        return advise(userId, careerId, ids, promotable, Timing.NONE);
    }

    /**
     * GROWTH-CAL madde 2: A takıma alma en az minPromotionAge (16) yaşında. Altında öneri yok, tarihli bilgi notu var (kıyas o tarihteki tahminle).
     * Kıyas aynı ufukta: gencin tahmini Güç'ü ile yedeğin aynı ufuktaki tahmini Güç'ü (yedeğin gelişimi ve yaşlanması dahil). Masa işi ve "Hazır"
     * durumu bu öneriden.
     */
    public Map<Long, Advice> advise(Long userId, Long careerId, List<Long> ids, boolean promotable, Timing timing) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Context ctx = context(userId, careerId, ids, promotable ? PROMOTE_SEASONS : 0);
        Map<String, Backup> backups = new HashMap<>();
        Map<Long, Advice> out = new HashMap<>();
        int minAge = academyProperties.getMinPromotionAge();
        for (FitPlayer player : ctx.targets()) {
            PlayerPositionAdvice pos = ctx.positions().get(player.getId());
            PlayerRecommendation rec = ctx.recommendations().get(player.getId());
            Plan grow = growPlan(rec);
            Promote promote = null;
            PromoteInfo info = null;
            LocalDate eligible = timing.eligibleOn().get(player.getId());
            boolean young = Objects.nonNull(player.getAge()) && player.getAge() < minAge;
            Optional<String> target = promotable ? target(player, pos, ctx.slotPositions()) : Optional.empty();
            if (promotable && young && Objects.nonNull(eligible) && target.isEmpty()) {
                // Taktikte uygun slot yok: kıyas yok ama tarihli not yine çıkar.
                info = new PromoteInfo(eligible, eligibleText(eligible, minAge), null, null, null, null);
            }
            if (target.isPresent()) {
                String at = target.get();
                int horizon = young && Objects.nonNull(eligible) ? seasonsUntil(timing.gameDate(), eligible) : PROMOTE_SEASONS;
                double strength = best(projector.project(player, grow, horizon, horizon).grown(), at);
                int slots = slotCount(ctx.slotPositions(), at);
                Backup backup = backups.computeIfAbsent(at + "#" + slots + "#" + horizon, (k) -> backup(projectedAt(ctx.squad(), at, player.getId(), horizon), slots));
                PromotionDecision decision = decision(player.getAge(), minAge, strength, backup);
                if (decision == PromotionDecision.PROMOTE) {
                    promote = new Promote(at, round1(strength), Objects.isNull(backup) ? null : backup.name(), Objects.isNull(backup) ? null : round1(backup.strength()),
                            promoteText(player.getName(), at, strength, backup));
                } else if (decision == PromotionDecision.INFO && Objects.nonNull(eligible)) {
                    info = new PromoteInfo(eligible, eligibleText(eligible, minAge), at, round1(strength), Objects.isNull(backup) ? null : backup.name(),
                            Objects.isNull(backup) ? null : round1(backup.strength()));
                }
            }
            out.put(player.getId(), new Advice(Objects.isNull(pos) ? null : pos.getBestOverallPosition(), Objects.isNull(pos) ? null : pos.getSideReason(),
                    Objects.isNull(pos) || Objects.isNull(pos.getConversion()) ? null : pos.getConversion().getPosition(), planAdvice(rec), promote, info));
        }
        return out;
    }

    /** Karar: yaş bilinmiyorsa yok; minAge altında bilgi notu; yaş tamam ve (aynı ufukta) yedeği geçiyorsa öneri. */
    public static PromotionDecision decision(Integer age, int minAge, double strength, Backup projectedBackup) {
        if (Objects.isNull(age)) {
            return PromotionDecision.NONE;
        }
        if (age < minAge) {
            return PromotionDecision.INFO;
        }
        return promotes(strength, projectedBackup) ? PromotionDecision.PROMOTE : PromotionDecision.NONE;
    }

    public static LocalDate eligibleOn(LocalDate dob, int minAge) {
        return Objects.isNull(dob) ? null : dob.plusYears(minAge);
    }

    /** Oyun tarihinden hedef tarihe kaç sezon (yukarı yuvarlanmış yıl, en az 1); oyun tarihi yoksa 1. */
    public static int seasonsUntil(LocalDate gameDate, LocalDate date) {
        if (Objects.isNull(gameDate) || Objects.isNull(date) || !date.isAfter(gameDate)) {
            return 1;
        }
        return (int) Math.max(1, Math.ceil(java.time.temporal.ChronoUnit.DAYS.between(gameDate, date) / DAYS_PER_SEASON));
    }

    public static String eligibleText(LocalDate eligibleOn, int minAge) {
        return minAge + " yaşında A takıma alınabilir · " + eligibleOn.format(DATE);
    }

    /** Mevkiyi listeleyen kadro oyuncularının `seasons` sonraki tahmini Güç'ü (Dengeli, A takım; gelişim ve yaşlanma dahil). */
    private List<Backup> projectedAt(List<FitPlayer> squad, String position, Long exclude, int seasons) {
        return squad.stream().filter((p) -> p.getPositions().contains(position) && !p.getId().equals(exclude))
                .map((p) -> new Backup(p.getName(), best(projector.project(p, plans.general(), seasons).grown(), position))).toList();
    }

    /** ACADEMY-b paneli: Ne yapmalıyım? · Hangi mevkide? · Takımdaki yeri. Hepsi tahmin. */
    public Optional<Detail> detail(Long userId, Long careerId, Long playerId) {
        return detail(userId, careerId, playerId, false, Timing.NONE);
    }

    /** academy: altyapı oyuncusu (16 yaşına ya da 1 sezona kadar altyapı tavanı, sonra A takım); kadro genci baştan A takım. */
    public Optional<Detail> detail(Long userId, Long careerId, Long playerId, boolean academy, Timing timing) {
        Context ctx = context(userId, careerId, List.of(playerId), academy ? PROMOTE_SEASONS : 0);
        if (ctx.targets().isEmpty()) {
            return Optional.empty();
        }
        FitPlayer player = ctx.targets().get(0);
        LocalDate eligible = timing.eligibleOn().get(playerId);
        boolean young = academy && Objects.nonNull(player.getAge()) && player.getAge() < academyProperties.getMinPromotionAge() && Objects.nonNull(eligible);
        int academySeasons = !academy ? 0 : young ? seasonsUntil(timing.gameDate(), eligible) : PROMOTE_SEASONS;
        PlayerPositionAdvice pos = ctx.positions().get(playerId);
        PlayerRecommendation rec = ctx.recommendations().get(playerId);
        String bestPosition = Objects.isNull(pos) || Objects.isNull(pos.getBestOverallPosition()) ? player.getPositions().get(0) : pos.getBestOverallPosition();

        Optional<Option> rolePlan = Objects.isNull(rec) ? Optional.empty()
                : rec.options().stream().filter((o) -> "ROLE".equals(o.kind())).max(Comparator.comparingDouble(Option::strength2));
        String planPosition = Objects.nonNull(rec) && Objects.nonNull(rec.recommended()) ? rec.recommended().position() : rolePlan.map(Option::position).orElse(bestPosition);
        List<PlanRow> rows = new java.util.ArrayList<>();
        rows.add(planRow(player, plans.general(), plans.general().name(), "GENERAL", planPosition, academySeasons));
        rolePlan.flatMap((o) -> plans.byKey(o.planKey()).map((p) -> planRow(player, p, o.position() + " · " + o.roleName(), "ROLE", o.position(), academySeasons)))
                .ifPresent(rows::add);
        if (Objects.nonNull(pos) && Objects.nonNull(pos.getConversion())) {
            String target = pos.getConversion().getPosition();
            plans.positionPlan(target).map((p) -> planRow(player, p, target + " mevki", "POSITION", target, academySeasons)).ifPresent(rows::add);
        }
        WhatToDo whatToDo = new WhatToDo(sentence(rec, rows), rows, true, assumption(academy, academySeasons));
        WhichPosition which = new WhichPosition(bestPosition, Objects.isNull(pos) ? null : pos.getBestOverall(), player.getPositions(),
                Objects.isNull(pos) || Objects.isNull(pos.getConversion()) ? null : pos.getConversion().getText(), Objects.isNull(pos) ? null : pos.getSideReason());

        String target = target(player, pos, ctx.slotPositions()).orElse(bestPosition);
        int slots = slotCount(ctx.slotPositions(), target);
        List<Ranked> ranked = ctx.squad().stream().filter((p) -> p.getPositions().contains(target) && !p.getId().equals(playerId))
                .map((p) -> new Ranked(p, best(p, target))).sorted(Comparator.comparingDouble(Ranked::strength).reversed()).toList();
        // Slot sayısından az oyuncu varsa boş slot var: ilk 11 eşiği yok (en zayıf ilk 11 değil, boş yer).
        Ranked starter = ranked.size() >= slots ? ranked.get(slots - 1) : null;
        Ranked backup = ranked.size() > slots ? ranked.get(slots) : null;
        Plan grow = growPlan(rec);
        List<Double> strengths = java.util.stream.IntStream.rangeClosed(1, TEAM_SEASONS)
                .mapToObj((s) -> round1(best(projector.project(player, grow, s, academySeasons).grown(), target))).toList();
        // GROWTH-CAL: kıyas aynı ufukta — ilk 11 ve yedeğin her sezondaki tahmini Güç'ü (gelişim ve yaşlanma dahil).
        TeamPlace team = new TeamPlace(target, positionAdvice.hasMainTactic(userId, careerId) ? "MAIN" : "AUTO", slots,
                Objects.isNull(starter) ? null : starter.player().getName(), Objects.isNull(starter) ? null : round1(starter.strength()),
                Objects.isNull(backup) ? null : backup.player().getName(), Objects.isNull(backup) ? null : round1(backup.strength()),
                strengths, seasonToPass(strengths, projectedSeasons(backup, target)), seasonToPass(strengths, projectedSeasons(starter, target)));
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

    private PlanRow planRow(FitPlayer player, Plan plan, String label, String kind, String position, int academySeasons) {
        return new PlanRow(label, kind, position, overallAt(player, plan, 1, position, academySeasons), overallAt(player, plan, 2, position, academySeasons));
    }

    /** Ufuktaki her sezon (1..3) için oyuncunun tahmini Güç'ü (Dengeli, A takım); oyuncu yoksa null (boş yer). */
    private List<Double> projectedSeasons(Ranked ranked, String position) {
        if (Objects.isNull(ranked)) {
            return null;
        }
        return java.util.stream.IntStream.rangeClosed(1, TEAM_SEASONS).mapToObj((s) -> best(projector.project(ranked.player(), plans.general(), s).grown(), position)).toList();
    }

    /** Panel varsayım satırı (tek satır): altyapı ve A takım kazanç tavanları. */
    private String assumption(boolean academy, int academySeasons) {
        int a = growthConfig.getAcademyMaxGain();
        int f = growthConfig.getFirstTeamMaxGain();
        if (!academy) {
            return String.format(Locale.ROOT, "Tahmin: A takımda (sezonda en çok +%d OVR).", f);
        }
        return academySeasons <= 1 ? String.format(Locale.ROOT, "Tahmin: 1. sezon altyapıda (en çok +%d OVR), 2. sezon A takımda (en çok +%d OVR).", a, f)
                : String.format(Locale.ROOT, "Tahmin: %d sezon altyapıda (16 yaşına kadar, sezonda en çok +%d OVR), sonra A takımda (en çok +%d OVR).", academySeasons, a, f);
    }

    /** Planla `seasons` sonra hedef mevkideki tahmini OVR (mevki formülü; bilinmezse genel OVR). */
    private int overallAt(FitPlayer player, Plan plan, int seasons, String position, int academySeasons) {
        PlanProjector.PlanProjection p = projector.project(player, plan, seasons, academySeasons);
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
            PlanRow chosen = rows.stream().filter((r) -> r.position().equals(o.position()) && r.kind().equals(o.kind())).findFirst()
                    .or(() -> rows.stream().filter((r) -> r.position().equals(o.position()) && !"GENERAL".equals(r.kind())).findFirst()).orElse(rows.get(0));
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
    /** Aynı ufukta: sezon i'de gencin Güç'ü, rakibin aynı sezondaki tahmini Güç'ünü geçiyorsa; rakip yoksa 1 (boş yer); geçmezse null. */
    public static Integer seasonToPass(List<Double> strengths, List<Double> thresholds) {
        if (Objects.isNull(thresholds)) {
            return 1;
        }
        for (int i = 0; i < strengths.size() && i < thresholds.size(); i++) {
            if (strengths.get(i) > thresholds.get(i)) {
                return i + 1;
            }
        }
        return null;
    }

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
