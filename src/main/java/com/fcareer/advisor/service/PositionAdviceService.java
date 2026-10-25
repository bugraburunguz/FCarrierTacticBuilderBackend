package com.fcareer.advisor.service;

import com.fcareer.advisor.configuration.PositionProperties;
import com.fcareer.advisor.model.response.AdvisorResponses.Conversion;
import com.fcareer.advisor.model.response.AdvisorResponses.PlayerPositionAdvice;
import com.fcareer.advisor.model.response.StarterTactic;
import com.fcareer.fitengine.configuration.FitEngineProperties;
import com.fcareer.career.desk.SpoilerPolicy;
import org.springframework.beans.factory.ObjectProvider;
import com.fcareer.development.plan.DevelopmentPlans;
import com.fcareer.development.plan.PlanProjector;
import com.fcareer.development.recommend.DevelopmentRecommender;
import com.fcareer.usertactic.UserTacticService;
import com.fcareer.usertactic.model.UserTacticModels;
import com.fcareer.advisor.model.response.AdvisorResponses.PositionOption;
import com.fcareer.career.service.CareerService;
import com.fcareer.catalog.service.PlayerService;
import com.fcareer.catalog.service.PositionOverallService;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.FitRole;
import com.fcareer.fitengine.model.WeaponState;
import com.fcareer.fitengine.model.response.RoleFitResponse;
import com.fcareer.fitengine.service.RoleFitRouter;
import com.fcareer.roles.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PositionAdviceService {

    private static final int POSITIONS_SHOWN = 3;
    private static final int REASONS_SHOWN = 2;
    private static final double POSITION_GAIN = 3.0;
    private static final int MAX_POSITION_DROP = 6;
    private static final List<String> OUTFIELD_POSITIONS = List.of("LB", "RB", "CB", "CDM", "CM", "CAM", "LM", "RM", "LW", "RW", "ST");
    private static final String GOALKEEPER = "GK";
    private static final int BOTTOM_THIRD = 3;
    private static final int ELEVEN = 11;
    private static final int YOUTH_SEASONS = 2;

    private final RoleService roleService;
    private final RoleFitRouter roleFitRouter;
    private final CareerService careerService;
    private final PlayerService playerService;
    private final PositionOverallService positionOverallService;
    private final PositionProperties positionProperties;
    private final SideScorer sideScorer;
    private final DevelopmentPlans developmentPlans;
    private final PlanProjector planProjector;
    private final DevelopmentRecommender.Config youth;
    private final UserTacticService userTactics;
    private final SpoilerPolicy spoilerPolicy;
    /** Başlangıç önerisi PositionAdviceService'e bağlı (bestScores): döngü olmasın diye tembel. */
    private final ObjectProvider<StarterTacticService> starterTactics;
    private final FitEngineProperties fitProperties;

    public List<PlayerPositionAdvice> forCareer(Long userId, Long careerId) {
        CareerService.CareerSquad squad = careerService.squadPlayerIds(userId, careerId);
        if (squad.playerIds().isEmpty()) {
            return List.of();
        }
        // Spoiler: SCOUT_RANGE kariyerinde gençlerin tahmini bandın alt sınırıyla (DevelopmentRecommender ile aynı).
        boolean ranged = spoilerPolicy.ranged(careerId);
        List<FitPlayer> players = playerService.fitPlayers(squad.playerIds(), squad.gameVersionId(), careerId).stream()
                .map((p) -> ranged && Objects.nonNull(p.getPotential()) ? p.toBuilder().potential(SpoilerPolicy.low(p.getPotential())).build() : p).toList();
        return advise(players, teamContext(userId, careerId, players));
    }

    public List<PlayerPositionAdvice> advise(List<FitPlayer> players) {
        return advise(players, TeamContext.NONE);
    }

    /**
     * FIT-3 kararları: dönüşüm için takım bağlamı. cutoff: ilk 11'in Güç alt üçte birinin üst sınırı (null → eşik yok); tacticPositions: ana taktiğin
     * (yoksa başlangıç önerisinin) mevkileri — dönüşüm yalnız bunlara (null → kısıt yok).
     */
    public record TeamContext(Double cutoff, java.util.Set<String> tacticPositions) {
        public static final TeamContext NONE = new TeamContext(null, null);
    }

    /** Taktik slotu: mevki, rol ve dizilen oyuncu (ana taktik ya da başlangıç önerisi). */
    private record TacticSlot(String position, String roleId, Long playerId) {
    }

    public TeamContext teamContext(Long userId, Long careerId, List<FitPlayer> players) {
        List<TacticSlot> slots = tacticSlots(userId, careerId);
        java.util.Set<String> positions = slots.stream().map(TacticSlot::position).collect(java.util.stream.Collectors.toSet());
        return new TeamContext(startingElevenCutoff(slots, players), positions);
    }

    /** ACADEMY-b: hesap ana taktikten mi (true) yoksa başlangıç önerisinden mi ("Otomatik öneri")? */
    public boolean hasMainTactic(Long userId, Long careerId) {
        UserTacticModels.UserTactic main = userTactics.main(userId, careerId);
        return Objects.nonNull(main) && Objects.nonNull(main.slots()) && !main.slots().isEmpty();
    }

    /** ACADEMY-b: ana taktiğin (yoksa başlangıç önerisinin) slot mevkileri, tekrarlarıyla (iki CB slotu → iki "CB"). */
    public List<String> tacticSlotPositions(Long userId, Long careerId) {
        return tacticSlots(userId, careerId).stream().map(TacticSlot::position).toList();
    }

    private List<TacticSlot> tacticSlots(Long userId, Long careerId) {
        UserTacticModels.UserTactic main = userTactics.main(userId, careerId);
        if (Objects.nonNull(main) && Objects.nonNull(main.slots()) && !main.slots().isEmpty()) {
            Map<String, Long> lineup = Objects.isNull(main.lineup()) ? Map.of() : main.lineup();
            return main.slots().stream().map((s) -> new TacticSlot(String.valueOf(s.get("position")), String.valueOf(s.get("roleId")),
                    lineup.get(String.valueOf(s.get("slot"))))).toList();
        }
        StarterTactic starter = starterTactics.getObject().starter(userId, careerId);
        if (Objects.isNull(starter) || Objects.isNull(starter.slots())) {
            return List.of();
        }
        Map<String, Long> lineup = Objects.isNull(starter.lineup()) ? Map.of() : starter.lineup();
        return starter.slots().stream().map((s) -> new TacticSlot(s.position(), s.roleId(), lineup.get(s.slotId()))).toList();
    }

    /**
     * FIT-3 kararı (2): takımın ilk 11'inin Güç alt üçte birinin üst sınırı (ilk 11 küçükten büyüğe, ⌊n/3⌋. değer). Dönüşüm önerilen mevkideki Güç bunu
     * geçmeli. Üçte bir boşsa eşik yok.
     */
    public static Double lowerThirdCutoff(List<Double> strengths) {
        List<Double> sorted = strengths.stream().sorted().toList();
        int bottom = sorted.size() / BOTTOM_THIRD;
        return bottom == 0 ? null : sorted.get(bottom - 1);
    }

    /** İlk 11: ana taktiğin (yoksa başlangıç önerisinin) dizilişi, slot rolüyle Güç; dizilen yoksa listelenen mevkide en iyi Güç'e göre ilk 11. */
    private Double startingElevenCutoff(List<TacticSlot> slots, List<FitPlayer> players) {
        Map<Long, FitPlayer> byId = players.stream().collect(java.util.stream.Collectors.toMap(FitPlayer::getId, (p) -> p, (a, b) -> a));
        Map<String, FitRole> roles = roleService.fitRolesById();
        List<Double> strengths = new ArrayList<>();
        for (TacticSlot slot : slots) {
            FitPlayer p = Objects.isNull(slot.playerId()) ? null : byId.get(slot.playerId());
            FitRole role = roles.get(slot.roleId());
            if (Objects.nonNull(p) && Objects.nonNull(role)) {
                strengths.add(roleFitRouter.forCareer(p, role, null, slot.position()).getScore());
            }
        }
        if (strengths.isEmpty()) {
            players.stream().map((p) -> bestListed(p, roles)).filter(Objects::nonNull).sorted(Comparator.reverseOrder()).limit(ELEVEN).forEach(strengths::add);
        }
        return lowerThirdCutoff(strengths);
    }

    private Double bestListed(FitPlayer player, Map<String, FitRole> roles) {
        return player.getPositions().stream().flatMap((pos) -> roles.values().stream().filter((r) -> r.getPositions().contains(pos))
                .map((r) -> roleFitRouter.forCareer(player, r, null, pos).getScore())).max(Double::compare).orElse(null);
    }

    /** Dönüşüm adayı mevkide ölçülen Güç: gençte (≤ maxAge, POT ≥ OVR + minHeadroom) o mevkinin planıyla 2 sezon sonraki en iyi rol Güç'ü. */
    private double conversionStrength(FitPlayer player, PositionOption option, Map<String, FitRole> roles) {
        boolean young = Objects.nonNull(player.getAge()) && Objects.nonNull(player.getPotential()) && player.getAge() <= youth.getMaxAge()
                && player.getPotential() - player.getOverall() >= youth.getMinHeadroom();
        if (!young) {
            return option.getScore();
        }
        String pos = option.getPosition();
        FitPlayer grown = planProjector.project(player, developmentPlans.positionPlan(pos).orElse(developmentPlans.general()), YOUTH_SEASONS).grown();
        return roles.values().stream().filter((r) -> r.getPositions().contains(pos) && !fitProperties.recommendationExcluded(r))
                .mapToDouble((r) -> roleFitRouter.forCareer(grown, r, null, pos).getScore())
                .max().orElse(option.getScore());
    }

    public List<PlayerPositionAdvice> advise(List<FitPlayer> players, TeamContext team) {
        Double startingCutoff = team.cutoff();
        Map<String, FitRole> roles = roleService.fitRolesById();
        List<PlayerPositionAdvice> advice = new ArrayList<>();
        for (FitPlayer player : players) {
            boolean keeper = player.getPositions().contains(GOALKEEPER);
            List<String> candidates = keeper ? List.of(GOALKEEPER) : OUTFIELD_POSITIONS;
            List<PositionOption> options = new ArrayList<>();
            // Dönüşüm adayları: listedeki mevkide en iyi rol; listede olmayanda öneri dışı roller (fcareer.fit.recommendation-excluded-roles) hariç en iyi.
            List<PositionOption> conversionOptions = new ArrayList<>();
            Map<String, Integer> positionOveralls = positionOveralls(player);
            for (String position : candidates) {
                PositionOption best = null;
                PositionOption bestTarget = null;
                for (FitRole role : roles.values()) {
                    if (!role.getPositions().contains(position)) {
                        continue;
                    }
                    RoleFitResponse fit = roleFitRouter.forCareer(player, role, null, position);
                    if (!plausible(player, position, fit, positionOveralls)) {
                        continue;
                    }
                    PositionOption option = PositionOption.builder().position(position).roleName(role.getName()).score(round1(fit.getScore()))
                            .natural(player.getPositions().contains(position))
                            .reasons(fit.getReasons() == null ? List.of() : fit.getReasons().stream().limit(REASONS_SHOWN).toList())
                            .build();
                    if (Objects.isNull(best) || option.getScore() > best.getScore()) {
                        best = option;
                    }
                    boolean target = option.isNatural() || !fitProperties.recommendationExcluded(role);
                    if (target && (Objects.isNull(bestTarget) || option.getScore() > bestTarget.getScore())) {
                        bestTarget = option;
                    }
                }
                if (Objects.nonNull(best)) {
                    options.add(best);
                }
                if (Objects.nonNull(bestTarget)) {
                    conversionOptions.add(bestTarget);
                }
            }
            options.sort(Comparator.comparingDouble(PositionOption::getScore).reversed());
            options = sideFirst(options, player);
            Map.Entry<String, Integer> picked = bestOverall(player);
            SideChoice.Choice side = sideChoice(player, picked.getKey(), positionOveralls, roles);
            Map.Entry<String, Integer> bestOverall = side.position().equals(picked.getKey()) ? picked : Map.entry(side.position(), positionOveralls.get(side.position()));
            Conversion conversion = keeper ? null : ConversionRule.find(conversionOptions, player.getPositions(), positionOveralls, player.getOverall(),
                    positionProperties.getConversionGain(), positionProperties.getConversionMaxOverallDrop(),
                    (pos) -> developmentPlans.positionPlan(pos).map((plan) -> pos + " mevki planı"),
                    (o) -> (Objects.isNull(team.tacticPositions()) || team.tacticPositions().contains(o.getPosition()))
                            && (Objects.isNull(startingCutoff) || conversionStrength(player, o, roles) > startingCutoff)).orElse(null);
            advice.add(PlayerPositionAdvice.builder().playerId(player.getId()).playerName(player.getName())
                    .listedPositions(player.getPositions()).best(options.stream().limit(POSITIONS_SHOWN).toList())
                    .note(note(options)).ownPositionBest(isOwnBest(options))
                    .bestOverallPosition(bestOverall.getKey()).bestOverall(bestOverall.getValue()).sideReason(side.reason()).conversion(conversion).build());
        }
        return advice;
    }

    public Map<Long, Map<String, Double>> bestScores(List<FitPlayer> players) {
        Map<String, FitRole> roles = roleService.fitRolesById();
        Map<Long, Map<String, Double>> matrix = new java.util.HashMap<>();
        for (FitPlayer player : players) {
            Map<String, Double> byPosition = new java.util.HashMap<>();
            List<String> candidates = player.getPositions().contains(GOALKEEPER) ? List.of(GOALKEEPER) : OUTFIELD_POSITIONS;
            Map<String, Integer> positionOveralls = positionOveralls(player);
            for (String position : candidates) {
                for (FitRole role : roles.values()) {
                    if (role.getPositions().contains(position)) {
                        RoleFitResponse fit = roleFitRouter.forCareer(player, role, null, position);
                        if (plausible(player, position, fit, positionOveralls)) {
                            byPosition.merge(position, fit.getScore(), Math::max);
                        }
                    }
                }
            }
            matrix.put(player.getId(), byPosition);
        }
        return matrix;
    }

    private Map<String, Integer> positionOveralls(FitPlayer player) {
        return positionOverallService.byPosition(player.getAttrs(), player.getPositions().contains(GOALKEEPER),
                player.getPositions().get(0), player.getOverall());
    }

    private boolean plausible(FitPlayer player, String position, RoleFitResponse fit, Map<String, Integer> positionOveralls) {
        if (player.getPositions().contains(position)) {
            return true;
        }
        Integer atPosition = positionOveralls.get(position);
        return fit.getBadge() != WeaponState.RED && Objects.nonNull(atPosition) && atPosition >= player.getOverall() - MAX_POSITION_DROP;
    }

    /**
     * KARIYER-UX FAZ 3.1 (BEHAVIOR CHANGE): simetrik eşitlikte (LB/RB, LM/RM, LW/RW) taraf listelenen mevkiden, sonra ayaktan seçilir;
     * listelenen mevki en iyiye {@code fcareer.position.listed-preference} kadar yakınsa o gösterilir (BestPositionRule). Eskiden ilk gelen sol seçiliyordu.
     */
    private Map.Entry<String, Integer> bestOverall(FitPlayer player) {
        Map.Entry<String, Integer> picked = BestPositionRule.pick(positionOveralls(player), player.getPositions(), player.getPreferredFoot(),
                positionProperties.getListedPreference());
        return Objects.nonNull(picked) ? picked : Map.entry(player.getPositions().get(0), player.getOverall());
    }

    /**
     * FIT-3 madde 13 (BEHAVIOR CHANGE): seçilen mevki kanattaysa ve ayna tarafın EA OVR'si en çok listedPreference düşükse taraf, iki tarafın en iyi
     * rol Güç'üne göre (ayak terimleri tarzı taşır); fark sideThreshold altındaysa BestPositionRule'un seçimi kalır.
     */
    private SideChoice.Choice sideChoice(FitPlayer player, String picked, Map<String, Integer> overalls, Map<String, FitRole> roles) {
        String mirror = SideChoice.mirror(picked);
        Integer here = overalls.get(picked);
        Integer there = Objects.isNull(mirror) ? null : overalls.get(mirror);
        if (Objects.isNull(here) || Objects.isNull(there) || there < here - positionProperties.getListedPreference()) {
            return new SideChoice.Choice(picked, null, false, null);
        }
        return SideChoice.choose(picked, sideScorer.sideBests(player, picked, mirror, roles.values()), player, positionProperties.getSideThreshold());
    }

    /** Öneri listesinde eşit puanlı simetrik mevkilerin sırası aynı kurala göre (aday listesi soldan başladığı için sol öne geçiyordu). */
    private List<PositionOption> sideFirst(List<PositionOption> sorted, FitPlayer player) {
        Map<String, PositionOption> byPosition = sorted.stream().collect(java.util.stream.Collectors.toMap(PositionOption::getPosition, o -> o, (a, b) -> a, java.util.LinkedHashMap::new));
        return BestPositionRule.sideFirst(sorted.stream().map(PositionOption::getPosition).toList(), p -> byPosition.get(p).getScore(),
                player.getPositions(), player.getPreferredFoot()).stream().map(byPosition::get).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private boolean isOwnBest(List<PositionOption> sorted) {
        if (sorted.isEmpty()) {
            return true;
        }
        double bestNatural = sorted.stream().filter(PositionOption::isNatural).mapToDouble(PositionOption::getScore).max().orElse(0);
        return sorted.get(0).isNatural() || sorted.get(0).getScore() - bestNatural < POSITION_GAIN;
    }

    private String note(List<PositionOption> sorted) {
        if (sorted.isEmpty()) {
            return null;
        }
        PositionOption first = sorted.get(0);
        double bestNatural = sorted.stream().filter(PositionOption::isNatural).mapToDouble(PositionOption::getScore).max().orElse(0);
        if (first.isNatural()) {
            return "Listelenen mevkilerinde en iyi " + first.getPosition() + " (" + first.getRoleName() + ").";
        }
        if (first.getScore() - bestNatural >= POSITION_GAIN) {
            return String.format(Locale.ROOT, "Mevki listesinde yok ama %s (%s) rolünde %.1f puanla listelenen mevkilerinden daha iyi.",
                    first.getPosition(), first.getRoleName(), first.getScore());
        }
        return "Listelenen mevkilerinde daha güvenli.";
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
