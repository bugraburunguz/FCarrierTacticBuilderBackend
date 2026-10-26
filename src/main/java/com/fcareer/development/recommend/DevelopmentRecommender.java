package com.fcareer.development.recommend;

import com.fcareer.advisor.model.response.AdvisorResponses.PlayerPositionAdvice;
import com.fcareer.advisor.service.PositionAdviceService;
import com.fcareer.career.desk.SpoilerPolicy;
import com.fcareer.fitengine.configuration.FitEngineProperties;
import com.fcareer.career.service.CareerService;
import com.fcareer.catalog.service.PlayerService;
import com.fcareer.development.plan.DevelopmentPlans;
import com.fcareer.development.plan.DevelopmentPlans.Plan;
import com.fcareer.development.plan.PlanProjector;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.FitRole;
import com.fcareer.fitengine.model.response.RoleFitResponse;
import com.fcareer.fitengine.service.RoleFitV3Service;
import com.fcareer.roles.service.RoleService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * FIT-3 madde 11: gelişim ve antrenman önerici. Kapsam: 23 yaş ve altı, potansiyeli OVR'sinden ≥ 5 yüksek. Aday planlar: oyuncunun mevkilerindeki rol
 * planları, takımın KALITE_ACIGI/IKISI slotlarının rol planları ve dönüşüm için mevki planı (madde 12). Her özel plan aynı hedefte (mevki, rol) Dengeli'ye
 * karşı 2 sezon sonrası Güç'le kıyaslanır; fark eşiğin altındaysa Dengeli önerilir. Hepsi tahmin, kalibre edilmedi (madde 10).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DevelopmentRecommender {

    private static final int SEASONS = 2;
    private static final int OPTIONS_SHOWN = 3;

    @Getter
    @Setter
    @ConfigurationProperties(prefix = "fcareer.development.recommend")
    public static class Config {
        /** verified:false */
        private int maxAge = 23;
        private int minHeadroom = 5;
        /** Özel plan Dengeli'yi 2 sezonda en az bu kadar Güç geçmezse Dengeli önerilir (verified:false). */
        private double minGain = 2.0;
        /** Karar (verified:false): en iyi rol, farklı mevkideki en iyi rolden 2 sezon sonra en az bu kadar Güç öndeyse "net hedef". */
        private double clearTargetGap = 5.0;
    }

    /** Takımın zayıf slotu (Masa sınıfı KALITE_ACIGI/IKISI); playerId slottaki oyuncu (karşılaştırma için). */
    public record WeakSlot(String slotId, String position, String roleId, Long playerId) {
    }

    /** Bir planın bir hedefteki tahmini. gain = strength2 − balancedStrength2 (aynı hedef, aynı rol). */
    public record Option(String planKey, String planName, String kind, String position, String roleId, String roleName, double strength1, double strength2,
                         Double roleFit2, double balancedStrength2, double gain, String incumbentName, Double incumbentStrength2) {
    }

    public record PlayerRecommendation(Long playerId, String playerName, Integer age, Integer overall, boolean balanced, Option recommended, List<Option> options,
                                       String text, boolean estimated, String reason) {
    }

    private final Config config;
    private final CareerService careerService;
    private final PlayerService playerService;
    private final RoleService roleService;
    private final RoleFitV3Service roleFit;
    private final DevelopmentPlans plans;
    private final PlanProjector projector;
    private final PositionAdviceService positionAdvice;
    private final SpoilerPolicy spoilerPolicy;
    private final FitEngineProperties fitProperties;

    public List<PlayerRecommendation> recommend(Long userId, Long careerId, List<WeakSlot> weakSlots) {
        return recommend(userId, careerId, weakSlots, null);
    }

    /**
     * ACADEMY-b: altyapı oyuncuları için öneri. Bağlam (taktik mevkileri, ilk 11 eşiği) A takım kadrosundan; adaylar yalnız verilen oyuncular. Sahiplik
     * squadPlayerIds'te doğrulanır; oyuncuları çağıran aynı kariyerden yükler.
     */
    public List<PlayerRecommendation> recommendFor(Long userId, Long careerId, List<FitPlayer> players) {
        return recommendFor(userId, careerId, players, 0);
    }

    /** GROWTH-CAL: academySeasons — tahminde kaç sezon altyapıda sayılır (altyapı oyuncusu 1: ikinci sezon A takımda; kadro genci 0). */
    public List<PlayerRecommendation> recommendFor(Long userId, Long careerId, List<FitPlayer> players, int academySeasons) {
        return recommend(userId, careerId, List.of(), players, academySeasons);
    }

    private List<PlayerRecommendation> recommend(Long userId, Long careerId, List<WeakSlot> weakSlots, List<FitPlayer> only) {
        return recommend(userId, careerId, weakSlots, only, 0);
    }

    private List<PlayerRecommendation> recommend(Long userId, Long careerId, List<WeakSlot> weakSlots, List<FitPlayer> only, int academySeasons) {
        CareerService.CareerSquad squad = careerService.squadPlayerIds(userId, careerId);
        // Spoiler (13 İlke 4): SCOUT_RANGE kariyerinde kesin potansiyel yerine bandın alt sınırı (kapsam ve tahmin ondan; baş boşluk geri hesaplanamaz).
        boolean ranged = spoilerPolicy.ranged(careerId);
        Function<FitPlayer, FitPlayer> banded = (p) -> ranged && Objects.nonNull(p.getPotential()) ? p.toBuilder().potential(SpoilerPolicy.low(p.getPotential())).build() : p;
        List<FitPlayer> players = playerService.fitPlayers(squad.playerIds(), squad.gameVersionId(), careerId).stream().map(banded).toList();
        Map<Long, FitPlayer> byId = players.stream().collect(Collectors.toMap(FitPlayer::getId, Function.identity(), (a, b) -> a));
        List<FitPlayer> candidates = (Objects.isNull(only) ? players : only.stream().map(banded).toList()).stream().filter(this::inScope).toList();
        // Dönüşüm adayı, Kadro/Roller ile aynı eşikten (ilk 11'in alt üçte biri; gençte 2 sezonluk tahmin).
        PositionAdviceService.TeamContext team = positionAdvice.teamContext(userId, careerId, players);
        Map<Long, PlayerPositionAdvice> advice = positionAdvice.advise(candidates, team).stream()
                .collect(Collectors.toMap(PlayerPositionAdvice::getPlayerId, Function.identity(), (a, b) -> a));
        // Karar: hedefler yalnız ana taktik (yoksa başlangıç önerisi) mevkilerinden — dönüşümle aynı kural; mevki bilinmiyorsa kısıt yok.
        Predicate<String> inTactic = (pos) -> Objects.isNull(team.tacticPositions()) || team.tacticPositions().isEmpty() || team.tacticPositions().contains(pos);
        // Aynı slot iki kez gelirse tek hesap.
        List<WeakSlot> slots = Objects.isNull(weakSlots) ? List.of() : weakSlots.stream().distinct().toList();
        List<PlayerRecommendation> out = candidates.stream().map((p) -> recommend(p, slots, byId, advice.get(p.getId()), inTactic, academySeasons)).toList();
        log.info("Development recommendations careerId : {} candidates : {}", careerId, out.size());
        return out;
    }

    private boolean inScope(FitPlayer p) {
        return Objects.nonNull(p.getAge()) && Objects.nonNull(p.getPotential()) && Objects.nonNull(p.getOverall()) && !p.getPositions().isEmpty()
                && p.getAge() <= config.getMaxAge() && p.getPotential() - p.getOverall() >= config.getMinHeadroom();
    }

    private PlayerRecommendation recommend(FitPlayer player, List<WeakSlot> slots, Map<Long, FitPlayer> byId, PlayerPositionAdvice advice, Predicate<String> inTactic,
                                           int academySeasons) {
        Plan balanced = plans.general();
        FitPlayer balanced2 = projector.project(player, balanced, SEASONS, academySeasons).grown();
        List<Option> options = new ArrayList<>();
        for (String position : player.getPositions().stream().filter(inTactic).toList()) {
            for (Plan plan : plans.rolePlans(position)) {
                option(player, plan, position, rolesOf(plan.baseRole(), position), balanced2, null, academySeasons).ifPresent(options::add);
            }
        }
        if (Objects.nonNull(advice) && Objects.nonNull(advice.getConversion())) {
            String position = advice.getConversion().getPosition();
            plans.positionPlan(position).flatMap((plan) -> option(player, plan, position, rolesAt(position), balanced2, null, academySeasons)).ifPresent(options::add);
        }
        // GROWTH-CAL: kanat/bek hedefinin tarafı FIT-3 taraf kuralıyla (en iyi mevki); karşı taraf seçenekleri çıkar. Takımın zayıf slot seçenekleri
        // filtrelenmez (slot doldurma önerisi kaybolmasın); en iyi mevki listede değilse filtre yok (listedeki bütün kanat seçenekleri düşmesin).
        List<Option> sided = new ArrayList<>(preferSide(options, sideTarget(Objects.isNull(advice) ? null : advice.getBestOverallPosition(), player.getPositions())));
        for (WeakSlot slot : slots) {
            if (Objects.equals(slot.playerId(), player.getId()) || Objects.isNull(slot.roleId())) {
                continue;
            }
            FitRole role = roleService.fitRolesById().get(slot.roleId());
            if (Objects.isNull(role) || fitProperties.recommendationExcluded(role) || !inTactic.test(slot.position())) {
                continue;
            }
            plans.rolePlan(role.getBaseRole(), slot.position()).flatMap((plan) -> option(player, plan, slot.position(), List.of(role), balanced2,
                    Objects.isNull(slot.playerId()) ? null : byId.get(slot.playerId()), academySeasons)).ifPresent(sided::add);
        }
        List<Option> distinct = sided.stream().collect(Collectors.toMap((o) -> o.planKey() + "@" + o.position() + "@" + o.roleId(), Function.identity(), (a, b) -> a))
                .values().stream().sorted(Comparator.comparingDouble(Option::gain).reversed()).toList();
        Decision decision = decide(distinct, config.getMinGain(), config.getClearTargetGap());
        // "Dengeli yeterli" metni kararın dayandığı hedefin (2 sezon sonra en güçlü) farkını yazar, en büyük farkı değil.
        double bestGain = Objects.isNull(decision.best()) ? 0 : decision.best().gain();
        String text = Objects.nonNull(decision.chosen()) ? text(player.getName(), decision.chosen())
                : "NO_CLEAR_TARGET".equals(decision.reason()) ? noClearTargetText(player.getName(), decision) : balancedText(player.getName(), bestGain);
        return new PlayerRecommendation(player.getId(), player.getName(), player.getAge(), player.getOverall(), Objects.isNull(decision.chosen()), decision.chosen(),
                distinct.stream().limit(OPTIONS_SHOWN).toList(), text, true, decision.reason());
    }

    /** Plan, hedef mevkideki aday rollerden 2. sezonda en güçlüsünde değerlendirilir; Dengeli aynı rolde. */
    private Optional<Option> option(FitPlayer player, Plan plan, String position, List<FitRole> roles, FitPlayer balanced2, FitPlayer incumbent, int academySeasons) {
        if (roles.isEmpty()) {
            return Optional.empty();
        }
        FitPlayer planned2 = projector.project(player, plan, SEASONS, academySeasons).grown();
        FitRole role = null;
        RoleFitResponse best = null;
        for (FitRole r : roles) {
            RoleFitResponse fit = roleFit.calculate(planned2, r, null, position);
            if (Objects.isNull(best) || fit.getCurrentFit() > best.getCurrentFit()) {
                best = fit;
                role = r;
            }
        }
        double strength1 = roleFit.calculate(projector.project(player, plan, 1, academySeasons).grown(), role, null, position).getCurrentFit();
        double balancedStrength2 = roleFit.calculate(balanced2, role, null, position).getCurrentFit();
        Double incumbentStrength = Objects.isNull(incumbent) ? null
                : roleFit.calculate(projector.project(incumbent, plans.general(), SEASONS).grown(), role, null, position).getCurrentFit();
        String planName = plan.kind() == DevelopmentPlans.Kind.POSITION ? position + " mevki" : plan.name();
        return Optional.of(new Option(plan.key(), planName, plan.kind().name(), position, role.getId(), role.getName(), round1(strength1), round1(best.getCurrentFit()),
                best.getLevelFit(), round1(balancedStrength2), round1(best.getCurrentFit() - balancedStrength2), Objects.isNull(incumbent) ? null : incumbent.getName(),
                Objects.isNull(incumbentStrength) ? null : round1(incumbentStrength)));
    }

    /** Öneri hedefi olabilecek roller: öneri dışı listesindekiler (fcareer.fit.recommendation-excluded-roles) hariç. */
    private List<FitRole> rolesOf(String baseRole, String position) {
        return rolesAt(position).stream().filter((r) -> r.getBaseRole().equals(baseRole)).toList();
    }

    private List<FitRole> rolesAt(String position) {
        return roleService.fitRolesById().values().stream().filter((r) -> r.getPositions().contains(position) && !fitProperties.recommendationExcluded(r)).toList();
    }

    /** chosen null → Dengeli; best: 2 sezon sonra en güçlü hedef (metin onun farkını yazar); reason: CLEAR_TARGET, NO_CLEAR_TARGET, SMALL_GAIN, NO_OPTIONS. */
    /** other: NO_CLEAR_TARGET'ta en iyi hedefe yakın kalan, başka gruptaki en iyi seçenek (gerekçe grupları adıyla yazar). */
    public record Decision(Option chosen, String reason, Option best, Option other) {
        public Decision(Option chosen, String reason) {
            this(chosen, reason, chosen, null);
        }

        public Decision(Option chosen, String reason, Option best) {
            this(chosen, reason, best, null);
        }
    }

    /**
     * FIT-3 kararı: 2 sezon sonraki en iyi rol (en yüksek Güç), farklı mevkideki en iyi rolden en az `clearTargetGap` öndeyse "net hedef" ve o plan
     * önerilir; değilse Dengeli ("Henüz net bir mevkisi yok"). Net hedefte de plan Dengeli'yi `minGain` kadar geçmezse Dengeli.
     */
    public static Decision decide(List<Option> options, double minGain, double clearTargetGap) {
        List<Option> special = options.stream().filter((o) -> !"GENERAL".equals(o.kind())).toList();
        Optional<Option> best = special.stream().max(Comparator.comparingDouble(Option::strength2));
        if (best.isEmpty()) {
            return new Decision(null, "NO_OPTIONS");
        }
        // GROWTH-CAL: kıyas farklı mevki GRUBUndaki en iyi rolle (kanat hücum LW/RW/LM/RM ve bek LB/RB tek grup; birbirine hep yakın çıkıyorlar).
        String bestGroup = positionGroup(best.get().position());
        Optional<Option> otherPosition = special.stream().filter((o) -> !positionGroup(o.position()).equals(bestGroup)).max(Comparator.comparingDouble(Option::strength2));
        if (otherPosition.isPresent() && best.get().strength2() - otherPosition.get().strength2() < clearTargetGap) {
            return new Decision(null, "NO_CLEAR_TARGET", best.get(), otherPosition.get());
        }
        return best.get().gain() >= minGain ? new Decision(best.get(), "CLEAR_TARGET") : new Decision(null, "SMALL_GAIN", best.get());
    }

    private static final Set<String> WING = Set.of("LW", "RW", "LM", "RM");
    private static final Set<String> FULLBACK = Set.of("LB", "RB");
    private static final Set<String> LEFT = Set.of("LW", "LM", "LB");
    private static final Set<String> RIGHT = Set.of("RW", "RM", "RB");

    /** "Net hedef" kıyasının bölge grubu: kanat hücum (LW, RW, LM, RM), bek (LB, RB); diğer mevkiler kendi başına. */
    public static String positionGroup(String position) {
        return WING.contains(position) ? "WING" : FULLBACK.contains(position) ? "FULLBACK" : position;
    }

    /**
     * Hedefin tarafı FIT-3 taraf kuralıyla: oyuncunun en iyi mevkisi (PositionAdviceService, taraf gerekçeli) kanat ya da bekteyse, aynı gruptaki karşı
     * tarafın seçenekleri çıkarılır. En iyi mevki kanat/bek değilse ya da bilinmiyorsa liste aynen.
     */
    public static List<Option> preferSide(List<Option> options, String bestPosition) {
        if (Objects.isNull(bestPosition) || !(WING.contains(bestPosition) || FULLBACK.contains(bestPosition))) {
            return options;
        }
        Set<String> otherSide = LEFT.contains(bestPosition) ? RIGHT : LEFT;
        String group = positionGroup(bestPosition);
        return options.stream().filter((o) -> !(positionGroup(o.position()).equals(group) && otherSide.contains(o.position()))).toList();
    }

    /** Taraf filtresinin hedefi: en iyi mevki oyuncunun listelediği mevkilerden biriyse o; değilse filtre yok (null). */
    public static String sideTarget(String bestPosition, List<String> listedPositions) {
        return Objects.nonNull(bestPosition) && Objects.nonNull(listedPositions) && listedPositions.contains(bestPosition) ? bestPosition : null;
    }

    /** Eşik kuralı tek başına (net hedef aranmaz). */
    public static Optional<Option> choose(List<Option> options, double minGain) {
        return Optional.ofNullable(decide(options, minGain, 0).chosen());
    }

    /** Gerekçede grup adı: Kanat (LW/RW/LM/RM), Bek (LB/RB); diğerleri mevki kısaltması. */
    public static String groupLabel(String position) {
        String group = positionGroup(position);
        return "WING".equals(group) ? "Kanat" : "FULLBACK".equals(group) ? "Bek" : position;
    }

    /** Karar: iki grup arasında kalan oyuncuya "Kanat ve CAM arasında, ikisinde de yakın"; gruplar bilinmiyorsa eski metin. */
    public static String noClearTargetText(String player, Decision decision) {
        if (Objects.isNull(decision) || Objects.isNull(decision.best()) || Objects.isNull(decision.other())) {
            return player + " · Dengeli plan: Henüz net bir mevkisi yok, esnek gelişsin";
        }
        return player + " · Dengeli plan: " + groupLabel(decision.best().position()) + " ve " + groupLabel(decision.other().position()) + " arasında, ikisinde de yakın";
    }

    /** "Figueiredo · CDM Deep-Lying Playmaker planı → 2 sezonda CDM gücü 71 (Dengeli: 67; Nyoni'nin tahmini 68)". */
    public static String text(String player, Option o) {
        String incumbent = Objects.isNull(o.incumbentName()) || Objects.isNull(o.incumbentStrength2()) ? ""
                : "; " + o.incumbentName() + genitive(o.incumbentName()) + " tahmini " + Math.round(o.incumbentStrength2());
        String plan = o.planName().startsWith(o.position() + " ") ? o.planName() : o.position() + " " + o.planName();
        return String.format(Locale.ROOT, "%s · %s planı → 2 sezonda %s gücü %d (Dengeli: %d%s)", player, plan, o.position(), Math.round(o.strength2()),
                Math.round(o.balancedStrength2()), incumbent);
    }

    public static String balancedText(String player, double bestGain) {
        return player + " · Dengeli plan yeterli: en güçlü hedefte özel plan 2 sezonda yalnız +" + Math.max(0, Math.round(bestGain)) + " Güç getiriyor";
    }

    /** Türkçe iyelik eki (ünlü uyumu): Nyoni'nin, Okafor'un, Kaya'nın. */
    private static String genitive(String name) {
        String lower = name.toLowerCase(new Locale("tr"));
        char last = lower.isEmpty() ? 'e' : lower.charAt(lower.length() - 1);
        char vowel = 'e';
        for (int i = lower.length() - 1; i >= 0; i--) {
            if ("aeıioöuü".indexOf(lower.charAt(i)) >= 0) {
                vowel = lower.charAt(i);
                break;
            }
        }
        String suffix = switch (vowel) {
            case 'a', 'ı' -> "ın";
            case 'o', 'u' -> "un";
            case 'ö', 'ü' -> "ün";
            default -> "in";
        };
        return "'" + ("aeıioöuü".indexOf(last) >= 0 ? "n" : "") + suffix;
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
