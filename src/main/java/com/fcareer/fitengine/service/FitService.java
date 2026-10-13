package com.fcareer.fitengine.service;

import com.fcareer.billing.configuration.BillingProperties;
import com.fcareer.billing.model.response.CreditedResponse;
import com.fcareer.billing.service.CreditGuard;
import com.fcareer.career.service.CareerService;
import com.fcareer.catalog.service.PlayerService;
import com.fcareer.common.exception.InvalidTacticException;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.configuration.FitEngineProperties;
import com.fcareer.fitengine.model.FitRole;
import com.fcareer.fitengine.model.IntentProfile;
import com.fcareer.fitengine.model.TacticRuleDefinition;
import com.fcareer.fitengine.model.request.CompareRequest;
import com.fcareer.fitengine.model.request.FitRoleRequest;
import com.fcareer.fitengine.model.request.SquadFitRequest;
import com.fcareer.fitengine.model.response.AttributeGap;
import com.fcareer.fitengine.model.response.FitRoleResult;
import com.fcareer.fitengine.model.response.PlayerRoleFit;
import com.fcareer.fitengine.model.response.RoleFitResponse;
import com.fcareer.fitengine.model.response.SlotAssignment;
import com.fcareer.fitengine.model.response.SquadFitResponse;
import com.fcareer.roles.service.RoleService;
import com.fcareer.tactics.service.TacticResolver;
import com.fcareer.tactics.service.TacticRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class FitService {

    private static final String SQUAD_FIT = "SQUAD_FIT";
    private static final String SLOT_ID = "SLOT";
    private static final int ROLE_REASONS = 3;

    private final PlayerService playerService;
    private final RoleService roleService;
    private final TacticResolver tacticResolver;
    private final TacticRuleService tacticRuleService;
    private final CareerService careerService;
    private final RoleFitService roleFitService;
    private final IntentFitService intentFitService;
    private final SquadFitService squadFitService;
    private final CreditGuard creditGuard;
    private final BillingProperties billingProperties;
    private final RoleFitV3Service roleFitV3Service;
    private final FitEngineProperties fitProperties;

    public FitRoleResult roleFit(FitRoleRequest request) {
        FitPlayer player = playerService.fitPlayers(List.of(request.getPlayerId()), request.getGameVersionId()).get(0);
        return evaluate(player, request.getRoleId(), request.getPosition(), request.getTags(), Objects.nonNull(request.getEngineVersion()) ? request.getEngineVersion() : fitProperties.getEngineVersion());
    }

    public List<PlayerRoleFit> rolesFor(Long playerId, Short gameVersionId, Long careerId) {
        FitPlayer player = playerService.fitPlayers(List.of(playerId), gameVersionId, careerId).get(0);
        List<FitRole> roles = roleService.fitRolesById().values().stream().sorted(Comparator.comparing(FitRole::getName)).toList();
        List<PlayerRoleFit> fits = new ArrayList<>();
        for (String position : player.getPositions()) {
            for (FitRole role : roles) {
                if (!role.getPositions().contains(position)) {
                    continue;
                }
                RoleFitResponse fit = roleFit(player, role, null, position, Objects.nonNull(careerId) ? fitProperties.getCareerEngineVersion() : fitProperties.getEngineVersion());
                fits.add(PlayerRoleFit.builder().position(position).roleId(role.getId()).roleName(role.getName()).focus(role.getFocus())
                        .score(Math.round(fit.getScore() * 10.0) / 10.0).projectedRating(fit.getProjectedRating())
                        .badge(fit.getBadge()).badgeText(fit.getBadgeText())
                        .engineVersion(fit.getEngineVersion()).currentFit(fit.getCurrentFit()).levelFit(fit.getLevelFit())
                        .reasons(Objects.isNull(fit.getReasons()) ? List.of() : fit.getReasons().stream().limit(ROLE_REASONS).toList())
                        .gaps(gaps(fit)).build());
            }
        }
        return fits;
    }

    private static List<AttributeGap> gaps(RoleFitResponse fit) {
        if (Objects.isNull(fit.getWeaponChecks())) {
            return List.of();
        }
        return fit.getWeaponChecks().stream()
                .filter(w -> w.getValue() < w.getEffectiveMin())
                .map(w -> AttributeGap.builder().attr(w.getAttr()).value(w.getValue()).needed(w.getEffectiveMin()).build())
                .toList();
    }

    public List<FitRoleResult> compare(CompareRequest request) {
        List<FitPlayer> players = playerService.fitPlayers(request.getIds(), request.getGameVersionId());
        return players.stream()
                .map(p -> evaluate(p, request.getRoleId(), request.getPosition(), request.getTags(), fitProperties.getEngineVersion()))
                .sorted(Comparator.comparingDouble((FitRoleResult r) -> r.getRoleFit().getScore()).reversed())
                .toList();
    }

    public CreditedResponse<SquadFitResponse> squadFit(Long userId, SquadFitRequest request) {
        List<Long> playerIds;
        Short gameVersion = request.getGameVersionId();
        boolean hasCareer = Objects.nonNull(request.getCareerId());
        boolean hasPlayers = Objects.nonNull(request.getPlayerIds()) && !request.getPlayerIds().isEmpty();
        if (hasCareer == hasPlayers) {
            log.warn("Squad source invalid hasCareer : {} hasPlayers : {}", hasCareer, hasPlayers);
            throw new InvalidTacticException("squad source (careerId or playerIds)");
        }
        if (hasCareer) {
            CareerService.CareerSquad squad = careerService.squadPlayerIds(userId, request.getCareerId());
            playerIds = squad.playerIds();
            gameVersion = squad.gameVersionId();
        } else {
            playerIds = request.getPlayerIds();
        }
        List<FitPlayer> players = playerService.fitPlayers(playerIds, gameVersion, hasCareer ? request.getCareerId() : null);
        List<IntentProfile> slots = tacticResolver.resolve(request.getTactic()).slots();
        List<TacticRuleDefinition> rules = tacticRuleService.enabledRules();
        return creditGuard.runCharged(userId, billingProperties.getSquadFitCost(), SQUAD_FIT,
                () -> squadFitService.solveBest(players, slots, rules, hasCareer ? fitProperties.getCareerEngineVersion() : fitProperties.getEngineVersion()));
    }

    /** Kredi harcamayan kadro dizilişi: yalnız atama + slot başına RoleFit% ve rozet. Kurallar, NLG, zayıf halka ve atak paterni ücretli /fit/squad'dadır (SEC-02). */
    public SquadFitResponse lineup(Long userId, SquadFitRequest request) {
        CareerService.CareerSquad squad = careerService.squadPlayerIds(userId, request.getCareerId());
        List<FitPlayer> players = playerService.fitPlayers(squad.playerIds(), squad.gameVersionId(), request.getCareerId());
        List<IntentProfile> slots = tacticResolver.resolve(request.getTactic()).slots();
        return withoutPaidInsights(squadFitService.solveBest(players, slots, tacticRuleService.enabledRules(), fitProperties.getCareerEngineVersion()));
    }

    private static SquadFitResponse withoutPaidInsights(SquadFitResponse full) {
        List<SlotAssignment> slots = Objects.isNull(full.getSlots()) ? List.of() : full.getSlots().stream().map(s -> SlotAssignment.builder()
                .slotId(s.getSlotId()).position(s.getPosition()).roleId(s.getRoleId()).roleName(s.getRoleName()).tags(s.getTags())
                .playerId(s.getPlayerId()).playerName(s.getPlayerName()).overall(s.getOverall()).combined(s.getCombined())
                .roleFit(Objects.isNull(s.getRoleFit()) ? null : RoleFitResponse.builder().playerId(s.getRoleFit().getPlayerId())
                        .playerName(s.getRoleFit().getPlayerName()).roleId(s.getRoleFit().getRoleId()).roleName(s.getRoleFit().getRoleName())
                        .score(s.getRoleFit().getScore()).projectedRating(s.getRoleFit().getProjectedRating()).badge(s.getRoleFit().getBadge())
                        .engineVersion(s.getRoleFit().getEngineVersion()).currentFit(s.getRoleFit().getCurrentFit()).levelFit(s.getRoleFit().getLevelFit())
                        .levelBandLo(s.getRoleFit().getLevelBandLo()).levelBandHi(s.getRoleFit().getLevelBandHi()).levelSample(s.getRoleFit().getLevelSample())
                        .outOfPosition(s.getRoleFit().isOutOfPosition()).build())
                .build()).toList();
        return SquadFitResponse.builder().squadFit(full.getSquadFit()).slots(slots).bench(full.getBench()).mirrored(full.isMirrored()).build();
    }

    private RoleFitResponse roleFit(FitPlayer player, FitRole role, IntentProfile intent, String position, String engineVersion) {
        return FitEngineV3.VERSION.equals(engineVersion) ? roleFitV3Service.calculate(player, role, intent, position) : roleFitService.calculate(player, role, intent, position);
    }

    private FitRoleResult evaluate(FitPlayer player, String roleId, String position, List<String> tags, String engineVersion) {
        FitRole role = roleService.getFitRole(roleId);
        if (Objects.isNull(tags) || tags.isEmpty()) {
            return FitRoleResult.builder().roleFit(roleFit(player, role, null, position, engineVersion)).build();
        }
        IntentProfile intent = tacticResolver.slotIntent(SLOT_ID, position, null, roleId, tags);
        return FitRoleResult.builder()
                .roleFit(roleFit(player, role, intent, position, engineVersion))
                .intentFit(intentFitService.calculate(player, intent))
                .build();
    }
}
