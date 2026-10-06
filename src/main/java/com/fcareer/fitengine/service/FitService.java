package com.fcareer.fitengine.service;

import com.fcareer.billing.configuration.BillingProperties;
import com.fcareer.billing.model.response.CreditedResponse;
import com.fcareer.billing.service.CreditGuard;
import com.fcareer.career.service.CareerService;
import com.fcareer.catalog.service.PlayerService;
import com.fcareer.common.exception.InvalidTacticException;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.FitRole;
import com.fcareer.fitengine.model.IntentProfile;
import com.fcareer.fitengine.model.TacticRuleDefinition;
import com.fcareer.fitengine.model.request.CompareRequest;
import com.fcareer.fitengine.model.request.FitRoleRequest;
import com.fcareer.fitengine.model.request.SquadFitRequest;
import com.fcareer.fitengine.model.response.FitRoleResult;
import com.fcareer.fitengine.model.response.PlayerRoleFit;
import com.fcareer.fitengine.model.response.RoleFitResponse;
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

    public FitRoleResult roleFit(FitRoleRequest request) {
        FitPlayer player = playerService.fitPlayers(List.of(request.getPlayerId()), request.getGameVersionId()).get(0);
        return evaluate(player, request.getRoleId(), request.getPosition(), request.getTags());
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
                RoleFitResponse fit = roleFitService.calculate(player, role, null, position);
                fits.add(PlayerRoleFit.builder().position(position).roleId(role.getId()).roleName(role.getName()).focus(role.getFocus())
                        .score(Math.round(fit.getScore() * 10.0) / 10.0).projectedRating(fit.getProjectedRating())
                        .badge(fit.getBadge()).badgeText(fit.getBadgeText())
                        .reasons(Objects.isNull(fit.getReasons()) ? List.of() : fit.getReasons().stream().limit(ROLE_REASONS).toList()).build());
            }
        }
        return fits;
    }

    public List<FitRoleResult> compare(CompareRequest request) {
        List<FitPlayer> players = playerService.fitPlayers(request.getIds(), request.getGameVersionId());
        return players.stream()
                .map(p -> evaluate(p, request.getRoleId(), request.getPosition(), request.getTags()))
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
                () -> squadFitService.solveBest(players, slots, rules));
    }

    /** Kredi harcamayan kadro dizilişi: seçili taktikte ilk 11 ve yedekler (gerekirse aynalanmış). */
    public SquadFitResponse lineup(Long userId, SquadFitRequest request) {
        CareerService.CareerSquad squad = careerService.squadPlayerIds(userId, request.getCareerId());
        List<FitPlayer> players = playerService.fitPlayers(squad.playerIds(), squad.gameVersionId(), request.getCareerId());
        List<IntentProfile> slots = tacticResolver.resolve(request.getTactic()).slots();
        return squadFitService.solveBest(players, slots, tacticRuleService.enabledRules());
    }

    private FitRoleResult evaluate(FitPlayer player, String roleId, String position, List<String> tags) {
        FitRole role = roleService.getFitRole(roleId);
        if (Objects.isNull(tags) || tags.isEmpty()) {
            return FitRoleResult.builder().roleFit(roleFitService.calculate(player, role, null, position)).build();
        }
        IntentProfile intent = tacticResolver.slotIntent(SLOT_ID, position, null, roleId, tags);
        return FitRoleResult.builder()
                .roleFit(roleFitService.calculate(player, role, intent, position))
                .intentFit(intentFitService.calculate(player, intent))
                .build();
    }
}
