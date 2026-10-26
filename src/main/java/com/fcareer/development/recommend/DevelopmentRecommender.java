package com.fcareer.development.recommend;

import com.fcareer.advisor.model.response.AdvisorResponses.PlayerPositionAdvice;
import com.fcareer.advisor.service.PositionAdviceService;
import com.fcareer.career.desk.SpoilerPolicy;
import com.fcareer.fitengine.configuration.FitEngineProperties;
import com.fcareer.career.service.CareerService;
import com.fcareer.catalog.service.PlayerService;
import com.fcareer.catalog.service.PositionOverallService;
import com.fcareer.common.service.MessageService;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.norms.RoleSeeds;
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
 * GT-a (tüm takım, recommendFor): Gençler (≤ maxAge) bugünkü kural; Zirve (maxAge+1 – veteranFromAge−1) mevcut rolünde Rol oturması düşükse rol planı,
 * değilse Dengeli; Tecrübeliler (≥ veteranFromAge) rolünde en ağır ve düşüşteki attribute'larla en çok örtüşen plan (1 sezon sonraki kayıp en az).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DevelopmentRecommender {

    private static final int SEASONS = 2;
    private static final int OPTIONS_SHOWN = 3;
    private static final String GOALKEEPER = "GK";
    private static final Locale TR = Locale.forLanguageTag("tr");

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
        /** GT-a (verified:false): Tecrübeliler bölümünün başladığı yaş (Zirve: maxAge + 1 ile bunun bir eksiği arası). */
        private int veteranFromAge = 30;
        /** GT-a (verified:false): tecrübeli oyuncuda "rolünde en ağır, düşüşteki" attribute sayısı (plan örtüşmesi bunlarla). */
        private int veteranHeavyAttrs = 6;
    }

    /** GT-a: Gençler (büyüme), Zirve (rolde incelme ya da koruma), Tecrübeliler (düşüşü yavaşlatma). */
    public enum AgeGroup { YOUTH, PEAK, VETERAN }

    public static AgeGroup ageGroup(Integer age, Config config) {
        if (Objects.isNull(age)) {
            return null;
        }
        return age <= config.getMaxAge() ? AgeGroup.YOUTH : age < config.getVeteranFromAge() ? AgeGroup.PEAK : AgeGroup.VETERAN;
    }

    /** Tecrübeli için aday plan: örtüşme (rol ağırlığı toplamı), 1 sezon sonraki mevki OVR ve Güç kaybı, korunan attribute adları (en ağırdan). */
    public record VeteranOption(String planKey, String label, double overlap, int ovrLoss, double strengthLoss, List<String> protectedAttrs) {
    }

    /** Takımın zayıf slotu (Masa sınıfı KALITE_ACIGI/IKISI); playerId slottaki oyuncu (karşılaştırma için). */
    public record WeakSlot(String slotId, String position, String roleId, Long playerId) {
    }

    /** Bir planın bir hedefteki tahmini. gain = strength2 − balancedStrength2 (aynı hedef, aynı rol). */
    public record Option(String planKey, String planName, String kind, String position, String roleId, String roleName, double strength1, double strength2,
                         Double roleFit2, double balancedStrength2, double gain, String incumbentName, Double incumbentStrength2) {
    }

    /** ageGroup (GT-a): YOUTH, PEAK, VETERAN. */
    public record PlayerRecommendation(Long playerId, String playerName, Integer age, Integer overall, boolean balanced, Option recommended, List<Option> options,
                                       String text, boolean estimated, String reason, String ageGroup) {
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
    private final PositionOverallService positionOverall;
    private final RoleSeeds roleSeeds;
    private final MessageService messages;

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
        // GT-a: verilen oyuncular (Gelişim, Altyapı) yaş bölümüne göre; liste verilmezse (Masa işleri) bugünkü kapsam: gelişim payı olan gençler.
        List<FitPlayer> candidates = Objects.isNull(only) ? players.stream().filter(this::inScope).toList()
                : only.stream().map(banded).filter((p) -> Objects.nonNull(ageGroup(p.getAge(), config)) && Objects.nonNull(p.getOverall()) && !p.getPositions().isEmpty())
                .toList();
        // Dönüşüm adayı, Kadro/Roller ile aynı eşikten (ilk 11'in alt üçte biri; gençte 2 sezonluk tahmin).
        PositionAdviceService.TeamContext team = positionAdvice.teamContext(userId, careerId, players);
        Map<Long, PlayerPositionAdvice> advice = positionAdvice.advise(candidates, team).stream()
                .collect(Collectors.toMap(PlayerPositionAdvice::getPlayerId, Function.identity(), (a, b) -> a));
        // Karar: hedefler yalnız ana taktik (yoksa başlangıç önerisi) mevkilerinden — dönüşümle aynı kural; mevki bilinmiyorsa kısıt yok.
        Predicate<String> inTactic = (pos) -> Objects.isNull(team.tacticPositions()) || team.tacticPositions().isEmpty() || team.tacticPositions().contains(pos);
        // Aynı slot iki kez gelirse tek hesap.
        List<WeakSlot> slots = Objects.isNull(weakSlots) ? List.of() : weakSlots.stream().distinct().toList();
        Map<Long, PositionAdviceService.LineupRole> lineup = Objects.isNull(only) ? Map.of() : positionAdvice.lineupRoles(userId, careerId);
        List<PlayerRecommendation> out = candidates.stream().map((p) -> switch (ageGroup(p.getAge(), config)) {
            case YOUTH -> inScope(p) ? recommend(p, slots, byId, advice.get(p.getId()), inTactic, academySeasons) : lowHeadroom(p);
            case PEAK -> peak(p, currentRole(p, lineup.get(p.getId()), advice.get(p.getId())));
            case VETERAN -> veteran(p, currentRole(p, lineup.get(p.getId()), advice.get(p.getId())));
        }).toList();
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
                distinct.stream().limit(OPTIONS_SHOWN).toList(), text, true, decision.reason(), AgeGroup.YOUTH.name());
    }

    /** GT-a: gelişim payı az genç (POT − OVR < minHeadroom ya da POT bilinmiyor): Dengeli. */
    private PlayerRecommendation lowHeadroom(FitPlayer p) {
        return balancedRecommendation(p, List.of(), p.getName() + " · Dengeli plan: gelişim payı az", "LOW_HEADROOM", AgeGroup.YOUTH);
    }

    private PlayerRecommendation balancedRecommendation(FitPlayer p, List<Option> options, String text, String reason, AgeGroup group) {
        return new PlayerRecommendation(p.getId(), p.getName(), p.getAge(), p.getOverall(), true, null, options, text, true, reason, group.name());
    }

    /** Oyuncunun mevcut rolü: ana taktikte (yoksa başlangıç önerisinde) dizildiği slot; değilse en iyi mevkisindeki en güçlü rol. */
    private record Target(String position, FitRole role) {
    }

    private Target currentRole(FitPlayer p, PositionAdviceService.LineupRole slot, PlayerPositionAdvice advice) {
        if (Objects.nonNull(slot) && Objects.nonNull(slot.roleId())) {
            FitRole role = roleService.fitRolesById().get(slot.roleId());
            if (Objects.nonNull(role)) {
                return new Target(slot.position(), role);
            }
        }
        String best = Objects.nonNull(advice) && p.getPositions().contains(advice.getBestOverallPosition()) ? advice.getBestOverallPosition() : p.getPositions().get(0);
        return rolesAt(best).stream().max(Comparator.comparingDouble((FitRole r) -> roleFit.calculate(p, r, null, best).getCurrentFit()))
                .map((r) -> new Target(best, r)).orElse(null);
    }

    /** Zirve: mevcut rolünde Rol oturması düşükse (WEAK, WRONG_ROLE) o rolün planı ("rolde incel"); değilse Dengeli. */
    private PlayerRecommendation peak(FitPlayer p, Target target) {
        if (Objects.isNull(target)) {
            return balancedRecommendation(p, List.of(), p.getName() + " · Dengeli plan", "NO_OPTIONS", AgeGroup.PEAK);
        }
        RoleFitResponse fit = roleFit.calculate(p, target.role(), null, target.position());
        String roleName = shortRoleName(target.role());
        Optional<Plan> plan = refinesRole(fit.getFitLabel()) ? plans.rolePlan(target.role().getBaseRole(), target.position()) : Optional.empty();
        if (plan.isPresent()) {
            FitPlayer balanced2 = projector.project(p, plans.general(), SEASONS).grown();
            Optional<Option> option = option(p, plan.get(), target.position(), List.of(target.role()), balanced2, null, 0);
            if (option.isPresent()) {
                return new PlayerRecommendation(p.getId(), p.getName(), p.getAge(), p.getOverall(), false, option.get(), List.of(option.get()),
                        peakText(p.getName(), target.position(), roleName, fit.getLevelFit(), true), true, "REFINE_ROLE", AgeGroup.PEAK.name());
            }
        }
        return balancedRecommendation(p, List.of(), peakText(p.getName(), target.position(), roleName, fit.getLevelFit(), false), "ROLE_FITS", AgeGroup.PEAK);
    }

    /**
     * Tecrübeli: 1 sezon sonra Dengeli'de düşen attribute'lardan rolünde en ağır veteranHeavyAttrs tanesi; mevkisinin rol ve mevki planlarından bunlarla en
     * çok örtüşen (rol ağırlığı toplamı) önerilir, eşitlikte mevki OVR ve Güç kaybı en az olan. Düşüş yoksa ya da örtüşen plan yoksa Dengeli.
     */
    private PlayerRecommendation veteran(FitPlayer p, Target target) {
        if (Objects.isNull(target)) {
            return balancedRecommendation(p, List.of(), p.getName() + " · Dengeli plan", "NO_OPTIONS", AgeGroup.VETERAN);
        }
        String position = target.position();
        FitPlayer balanced1 = projector.project(p, plans.general(), 1).grown();
        List<Attr> declining = p.getAttrs().keySet().stream().filter((a) -> balanced1.attr(a) < p.attr(a)).toList();
        if (declining.isEmpty()) {
            return balancedRecommendation(p, List.of(), noDeclineText(p.getName()), "NO_DECLINE", AgeGroup.VETERAN);
        }
        Map<Attr, Double> weights = roleSeeds.vector(position, target.role().getBaseRole(), target.role().getFocus()).orElseGet(() -> positionOverall.weights(position));
        List<Attr> heavy = declining.stream().filter((a) -> weights.getOrDefault(a, 0.0) > 0)
                .sorted(Comparator.comparingDouble((Attr a) -> weights.get(a)).reversed()).limit(config.getVeteranHeavyAttrs()).toList();
        int now = overallAt(p, position);
        double fitNow = roleFit.calculate(p, target.role(), null, position).getCurrentFit();
        List<Plan> candidates = new ArrayList<>(plans.rolePlans(position));
        plans.positionPlan(position).ifPresent(candidates::add);
        Map<String, Plan> byKey = new java.util.HashMap<>();
        List<VeteranOption> options = new ArrayList<>();
        for (Plan plan : candidates) {
            List<Attr> covered = heavy.stream().filter(plan.attributes()::contains).toList();
            FitPlayer planned1 = projector.project(p, plan, 1).grown();
            String label = plan.kind() == DevelopmentPlans.Kind.POSITION ? position + " mevki" : position + " · " + plan.name();
            options.add(new VeteranOption(plan.key(), label, round1(covered.stream().mapToDouble(weights::get).sum() * 100) / 100, now - overallAt(planned1, position),
                    round1(fitNow - roleFit.calculate(planned1, target.role(), null, position).getCurrentFit()),
                    covered.stream().limit(2).map(this::attrName).toList()));
            byKey.put(plan.key(), plan);
        }
        int balancedLoss = now - overallAt(balanced1, position);
        Optional<VeteranOption> chosen = chooseVeteran(options);
        if (chosen.isEmpty()) {
            return balancedRecommendation(p, List.of(), p.getName() + " · Dengeli plan: plansız tahmini " + signed(balancedLoss) + " OVR · tahmin", "NO_OVERLAP",
                    AgeGroup.VETERAN);
        }
        FitPlayer balanced2 = projector.project(p, plans.general(), SEASONS).grown();
        Optional<Option> option = option(p, byKey.get(chosen.get().planKey()), position, List.of(target.role()), balanced2, null, 0);
        if (option.isEmpty()) {
            return balancedRecommendation(p, List.of(), noDeclineText(p.getName()), "NO_OPTIONS", AgeGroup.VETERAN);
        }
        return new PlayerRecommendation(p.getId(), p.getName(), p.getAge(), p.getOverall(), false, option.get(), List.of(option.get()),
                veteranText(p.getName(), balancedLoss, chosen.get()), true, "SLOW_DECLINE", AgeGroup.VETERAN.name());
    }

    /** Mevkideki OVR (mevki formülü; bilinmezse genel OVR). */
    private int overallAt(FitPlayer p, String position) {
        Integer at = positionOverall.byPosition(p.getAttrs(), p.getPositions().contains(GOALKEEPER), p.getPositions().get(0), p.getOverall()).get(position);
        return Objects.isNull(at) ? p.getOverall() : at;
    }

    private String attrName(Attr attr) {
        return messages.getMessage(attr.getMessageKey()).toLowerCase(TR);
    }

    /** Rol adı odaksız ("Box-to-Box (Balanced)" → "Box-to-Box"). */
    private static String shortRoleName(FitRole role) {
        String name = role.getName();
        int paren = name.indexOf(" (");
        return paren > 0 ? name.substring(0, paren) : name;
    }

    /** Zirve kuralı: Rol oturması etiketi WEAK ya da WRONG_ROLE ise rolde incelmeli. */
    public static boolean refinesRole(String fitLabel) {
        return "WEAK".equals(fitLabel) || "WRONG_ROLE".equals(fitLabel);
    }

    /** "Okafor · CM Box-to-Box planı: rol oturması %42, rolde incelsin" ya da "Okafor · Dengeli plan: CM Box-to-Box rolünde oturması iyi (%71)". */
    public static String peakText(String player, String position, String roleName, Double levelFit, boolean refine) {
        if (Objects.isNull(levelFit)) {
            return player + " · " + (refine ? position + " " + roleName + " planı: rolde incelsin" : "Dengeli plan: " + position + " " + roleName + " rolünü korusun");
        }
        long pct = Math.round(levelFit);
        return refine ? String.format(Locale.ROOT, "%s · %s %s planı: rol oturması %%%d, rolde incelsin", player, position, roleName, pct)
                : String.format(Locale.ROOT, "%s · Dengeli plan: %s %s rolünde oturması iyi (%%%d)", player, position, roleName, pct);
    }

    /** Tecrübeli seçimi: örtüşmesi olan planlardan en çok örtüşen; eşitlikte mevki OVR kaybı, sonra Güç kaybı en az. */
    public static Optional<VeteranOption> chooseVeteran(List<VeteranOption> options) {
        return options.stream().filter((o) -> o.overlap() > 0).max(Comparator.comparingDouble(VeteranOption::overlap)
                .thenComparing(Comparator.comparingInt(VeteranOption::ovrLoss).reversed())
                .thenComparing(Comparator.comparingDouble(VeteranOption::strengthLoss).reversed()));
    }

    /** "Vardy · Plansız tahmini −3 OVR, ST · Poacher planıyla −1 (bitiricilik ve pozisyon alma korunur) · tahmin". */
    public static String veteranText(String player, int balancedLoss, VeteranOption option) {
        String kept = option.protectedAttrs().isEmpty() ? "" : " (" + String.join(" ve ", option.protectedAttrs()) + " korunur)";
        return player + " · Plansız tahmini " + signed(balancedLoss) + " OVR, " + option.label() + " planıyla " + signed(option.ovrLoss()) + kept + " · tahmin";
    }

    public static String noDeclineText(String player) {
        return player + " · Dengeli plan: düşüş henüz başlamadı";
    }

    /** Kayıp → "−3", 0 → "±0", artış → "+1". */
    private static String signed(int loss) {
        return loss > 0 ? "−" + loss : loss == 0 ? "±0" : "+" + (-loss);
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
