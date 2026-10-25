package com.fcareer.advisor.service;

import com.fcareer.advisor.configuration.PositionProperties;
import com.fcareer.advisor.model.response.AdvisorResponses.Conversion;
import com.fcareer.advisor.model.response.AdvisorResponses.PlayerPositionAdvice;
import com.fcareer.development.plan.DevelopmentPlans;
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

    private final RoleService roleService;
    private final RoleFitRouter roleFitRouter;
    private final CareerService careerService;
    private final PlayerService playerService;
    private final PositionOverallService positionOverallService;
    private final PositionProperties positionProperties;
    private final SideScorer sideScorer;
    private final DevelopmentPlans developmentPlans;

    public List<PlayerPositionAdvice> forCareer(Long userId, Long careerId) {
        CareerService.CareerSquad squad = careerService.squadPlayerIds(userId, careerId);
        if (squad.playerIds().isEmpty()) {
            return List.of();
        }
        return advise(playerService.fitPlayers(squad.playerIds(), squad.gameVersionId(), careerId));
    }

    public List<PlayerPositionAdvice> advise(List<FitPlayer> players) {
        Map<String, FitRole> roles = roleService.fitRolesById();
        List<PlayerPositionAdvice> advice = new ArrayList<>();
        for (FitPlayer player : players) {
            boolean keeper = player.getPositions().contains(GOALKEEPER);
            List<String> candidates = keeper ? List.of(GOALKEEPER) : OUTFIELD_POSITIONS;
            List<PositionOption> options = new ArrayList<>();
            Map<String, Integer> positionOveralls = positionOveralls(player);
            for (String position : candidates) {
                PositionOption best = null;
                for (FitRole role : roles.values()) {
                    if (!role.getPositions().contains(position)) {
                        continue;
                    }
                    RoleFitResponse fit = roleFitRouter.forCareer(player, role, null, position);
                    if (!plausible(player, position, fit, positionOveralls)) {
                        continue;
                    }
                    if (Objects.isNull(best) || fit.getScore() > best.getScore()) {
                        best = PositionOption.builder().position(position).roleName(role.getName()).score(round1(fit.getScore()))
                                .natural(player.getPositions().contains(position))
                                .reasons(fit.getReasons() == null ? List.of() : fit.getReasons().stream().limit(REASONS_SHOWN).toList())
                                .build();
                    }
                }
                if (Objects.nonNull(best)) {
                    options.add(best);
                }
            }
            options.sort(Comparator.comparingDouble(PositionOption::getScore).reversed());
            options = sideFirst(options, player);
            Map.Entry<String, Integer> picked = bestOverall(player);
            SideChoice.Choice side = sideChoice(player, picked.getKey(), positionOveralls, roles);
            Map.Entry<String, Integer> bestOverall = side.position().equals(picked.getKey()) ? picked : Map.entry(side.position(), positionOveralls.get(side.position()));
            Conversion conversion = keeper ? null : ConversionRule.find(options, player.getPositions(), positionOveralls, player.getOverall(),
                    positionProperties.getConversionGain(), positionProperties.getConversionMaxOverallDrop(),
                    (pos) -> developmentPlans.positionPlan(pos).map((plan) -> pos + " mevki planı")).orElse(null);
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
