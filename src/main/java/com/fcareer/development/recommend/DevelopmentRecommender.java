package com.fcareer.development.recommend;

import com.fcareer.advisor.model.response.AdvisorResponses.PlayerPositionAdvice;
import com.fcareer.advisor.service.PositionAdviceService;
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
import java.util.function.Function;
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
    }

    /** Takımın zayıf slotu (Masa sınıfı KALITE_ACIGI/IKISI); playerId slottaki oyuncu (karşılaştırma için). */
    public record WeakSlot(String slotId, String position, String roleId, Long playerId) {
    }

    /** Bir planın bir hedefteki tahmini. gain = strength2 − balancedStrength2 (aynı hedef, aynı rol). */
    public record Option(String planKey, String planName, String kind, String position, String roleId, String roleName, double strength1, double strength2,
                         Double roleFit2, double balancedStrength2, double gain, String incumbentName, Double incumbentStrength2) {
    }

    public record PlayerRecommendation(Long playerId, String playerName, Integer age, Integer overall, boolean balanced, Option recommended, List<Option> options,
                                       String text, boolean estimated) {
    }

    private final Config config;
    private final CareerService careerService;
    private final PlayerService playerService;
    private final RoleService roleService;
    private final RoleFitV3Service roleFit;
    private final DevelopmentPlans plans;
    private final PlanProjector projector;
    private final PositionAdviceService positionAdvice;

    public List<PlayerRecommendation> recommend(Long userId, Long careerId, List<WeakSlot> weakSlots) {
        CareerService.CareerSquad squad = careerService.squadPlayerIds(userId, careerId);
        List<FitPlayer> players = playerService.fitPlayers(squad.playerIds(), squad.gameVersionId(), careerId);
        Map<Long, FitPlayer> byId = players.stream().collect(Collectors.toMap(FitPlayer::getId, Function.identity(), (a, b) -> a));
        List<FitPlayer> candidates = players.stream().filter(this::inScope).toList();
        Map<Long, PlayerPositionAdvice> advice = positionAdvice.advise(candidates).stream()
                .collect(Collectors.toMap(PlayerPositionAdvice::getPlayerId, Function.identity(), (a, b) -> a));
        List<WeakSlot> slots = Objects.isNull(weakSlots) ? List.of() : weakSlots;
        List<PlayerRecommendation> out = candidates.stream().map((p) -> recommend(p, slots, byId, advice.get(p.getId()))).toList();
        log.info("Development recommendations careerId : {} candidates : {}", careerId, out.size());
        return out;
    }

    private boolean inScope(FitPlayer p) {
        return Objects.nonNull(p.getAge()) && Objects.nonNull(p.getPotential()) && Objects.nonNull(p.getOverall()) && !p.getPositions().isEmpty()
                && p.getAge() <= config.getMaxAge() && p.getPotential() - p.getOverall() >= config.getMinHeadroom();
    }

    private PlayerRecommendation recommend(FitPlayer player, List<WeakSlot> slots, Map<Long, FitPlayer> byId, PlayerPositionAdvice advice) {
        Plan balanced = plans.general();
        FitPlayer balanced1 = projector.project(player, balanced, 1).grown();
        FitPlayer balanced2 = projector.project(player, balanced, SEASONS).grown();
        List<Option> options = new ArrayList<>();
        for (String position : player.getPositions()) {
            for (Plan plan : plans.rolePlans(position)) {
                option(player, plan, position, rolesOf(plan.baseRole(), position), balanced1, balanced2, null).ifPresent(options::add);
            }
        }
        for (WeakSlot slot : slots) {
            if (Objects.equals(slot.playerId(), player.getId()) || Objects.isNull(slot.roleId())) {
                continue;
            }
            FitRole role = roleService.fitRolesById().get(slot.roleId());
            if (Objects.isNull(role)) {
                continue;
            }
            plans.rolePlan(role.getBaseRole(), slot.position()).flatMap((plan) -> option(player, plan, slot.position(), List.of(role), balanced1, balanced2,
                    Objects.isNull(slot.playerId()) ? null : byId.get(slot.playerId()))).ifPresent(options::add);
        }
        if (Objects.nonNull(advice) && Objects.nonNull(advice.getConversion())) {
            String position = advice.getConversion().getPosition();
            plans.positionPlan(position).flatMap((plan) -> option(player, plan, position, rolesAt(position), balanced1, balanced2, null)).ifPresent(options::add);
        }
        List<Option> distinct = options.stream().collect(Collectors.toMap((o) -> o.planKey() + "@" + o.position() + "@" + o.roleId(), Function.identity(), (a, b) -> a))
                .values().stream().sorted(Comparator.comparingDouble(Option::gain).reversed()).toList();
        Optional<Option> chosen = choose(distinct, config.getMinGain());
        double bestGain = distinct.isEmpty() ? 0 : distinct.get(0).gain();
        String text = chosen.map((o) -> text(player.getName(), o)).orElseGet(() -> balancedText(player.getName(), bestGain));
        return new PlayerRecommendation(player.getId(), player.getName(), player.getAge(), player.getOverall(), chosen.isEmpty(), chosen.orElse(null),
                distinct.stream().limit(OPTIONS_SHOWN).toList(), text, true);
    }

    /** Plan, hedef mevkideki aday rollerden 2. sezonda en güçlüsünde değerlendirilir; Dengeli aynı rolde. */
    private Optional<Option> option(FitPlayer player, Plan plan, String position, List<FitRole> roles, FitPlayer balanced1, FitPlayer balanced2, FitPlayer incumbent) {
        if (roles.isEmpty()) {
            return Optional.empty();
        }
        FitPlayer planned2 = projector.project(player, plan, SEASONS).grown();
        FitRole role = null;
        RoleFitResponse best = null;
        for (FitRole r : roles) {
            RoleFitResponse fit = roleFit.calculate(planned2, r, null, position);
            if (Objects.isNull(best) || fit.getCurrentFit() > best.getCurrentFit()) {
                best = fit;
                role = r;
            }
        }
        double strength1 = roleFit.calculate(projector.project(player, plan, 1).grown(), role, null, position).getCurrentFit();
        double balancedStrength2 = roleFit.calculate(balanced2, role, null, position).getCurrentFit();
        Double incumbentStrength = Objects.isNull(incumbent) ? null
                : roleFit.calculate(projector.project(incumbent, plans.general(), SEASONS).grown(), role, null, position).getCurrentFit();
        String planName = plan.kind() == DevelopmentPlans.Kind.POSITION ? position + " mevki" : plan.name();
        return Optional.of(new Option(plan.key(), planName, plan.kind().name(), position, role.getId(), role.getName(), round1(strength1), round1(best.getCurrentFit()),
                best.getLevelFit(), round1(balancedStrength2), round1(best.getCurrentFit() - balancedStrength2), Objects.isNull(incumbent) ? null : incumbent.getName(),
                Objects.isNull(incumbentStrength) ? null : round1(incumbentStrength)));
    }

    private List<FitRole> rolesOf(String baseRole, String position) {
        return roleService.fitRolesById().values().stream().filter((r) -> r.getBaseRole().equals(baseRole) && r.getPositions().contains(position)).toList();
    }

    private List<FitRole> rolesAt(String position) {
        return roleService.fitRolesById().values().stream().filter((r) -> r.getPositions().contains(position)).toList();
    }

    /** En büyük kazançlı özel plan; kazanç eşiğin altındaysa boş (Dengeli). */
    public static Optional<Option> choose(List<Option> options, double minGain) {
        return options.stream().filter((o) -> !"GENERAL".equals(o.kind())).max(Comparator.comparingDouble(Option::gain)).filter((o) -> o.gain() >= minGain);
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
        return player + " · Dengeli plan yeterli: özel planlar 2 sezonda en çok +" + Math.max(0, Math.round(bestGain)) + " Güç getiriyor";
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
