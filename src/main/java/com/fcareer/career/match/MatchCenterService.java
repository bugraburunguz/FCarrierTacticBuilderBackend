package com.fcareer.career.match;

import com.fcareer.career.entity.CareerEntity;
import com.fcareer.career.match.MatchCenterModels.Formation;
import com.fcareer.career.match.MatchCenterModels.FormationSlot;
import com.fcareer.career.match.MatchCenterModels.LikelyStarter;
import com.fcareer.career.match.MatchCenterModels.LineRatings;
import com.fcareer.career.match.MatchCenterModels.Lines;
import com.fcareer.career.match.MatchCenterModels.Missing;
import com.fcareer.career.match.MatchCenterModels.Opponent;
import com.fcareer.career.match.MatchCenterModels.OpponentReport;
import com.fcareer.career.match.MatchCenterModels.PlayerRef;
import com.fcareer.career.match.MatchCenterModels.SetPieces;
import com.fcareer.career.match.MatchCenterModels.SquadMember;
import com.fcareer.career.match.MatchCenterModels.TeamMatchInfo;
import com.fcareer.career.match.MatchCenterModels.TeamRow;
import com.fcareer.career.service.CareerService;
import com.fcareer.catalog.service.PlayerService;
import com.fcareer.common.exception.NotFoundException;
import com.fcareer.fitengine.configuration.FitEngineProperties;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.response.SlotAssignment;
import com.fcareer.fitengine.service.SquadFitService;
import com.fcareer.ingestion.base.DumpMappings;
import com.fcareer.tactics.model.FormationDefinition;
import com.fcareer.tactics.model.request.TacticRequest;
import com.fcareer.tactics.service.FormationRegistry;
import com.fcareer.tactics.service.TacticResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class MatchCenterService {

    private static final String TEAM = "team";
    private static final int ELEVEN = 11;

    private final CareerService careerService;
    private final MatchCenterRepository repository;
    private final MatchProperties properties;
    private final FormationRegistry formationRegistry;
    private final TacticResolver tacticResolver;
    private final PlayerService playerService;
    private final SquadFitService squadFitService;
    private final FitEngineProperties fitProperties;

    @Transactional(readOnly = true)
    public List<Opponent> listOpponents(Long userId, Long careerId) {
        careerService.requireOwned(userId, careerId);
        return repository.findUserTeam(careerId).map((team) -> repository.findOpponents(careerId, team)).orElse(List.of());
    }

    @Transactional(readOnly = true)
    public OpponentReport buildReport(Long userId, Long careerId, Integer teamId) {
        CareerEntity career = careerService.requireOwned(userId, careerId);
        Optional<Integer> userTeam = repository.findUserTeam(careerId);
        TeamRow opponent = repository.findTeam(careerId, teamId).filter((t) -> userTeam.map((u) -> !u.equals(t.teamId())).orElse(true))
                .orElseThrow(() -> new NotFoundException(TEAM, teamId));
        List<Missing> missing = new ArrayList<>();
        if (userTeam.isEmpty()) {
            missing.add(Missing.USER_TEAM);
        }
        Optional<TeamMatchInfo> info = repository.findMatchInfo(careerId, teamId);
        Optional<FormationDefinition> matched = info.map(TeamMatchInfo::positions).flatMap(this::findClosestFormation);
        Formation formation = info.filter((i) -> Objects.nonNull(i.formationName()) && Objects.nonNull(i.positions()))
                .map((i) -> toFormation(i, matched.map(FormationDefinition::id).orElse(null))).orElse(null);
        if (Objects.isNull(formation)) {
            missing.add(Missing.FORMATION);
        }
        List<SquadMember> squad = repository.findSquad(careerId, teamId);
        if (squad.stream().allMatch((m) -> Objects.isNull(m.form()))) {
            missing.add(Missing.FORM);
        }
        List<LikelyStarter> eleven = Objects.isNull(formation) || matched.isEmpty() ? List.of() : solveLikelyEleven(career, squad, matched.get());
        if (eleven.size() < ELEVEN) {
            missing.add(Missing.LIKELY_ELEVEN);
        }
        if (!repository.hasSeasonStats(careerId, teamId)) {
            missing.add(Missing.SEASON_STATS);
        }
        SetPieces setPieces = info.map(this::findSetPieces).orElse(null);
        if (Objects.isNull(setPieces) || Stream.of(setPieces.captain(), setPieces.penaltyTaker(), setPieces.freeKickTaker(), setPieces.leftCornerTaker(),
                setPieces.rightCornerTaker()).allMatch(Objects::isNull)) {
            missing.add(Missing.SET_PIECES);
        }
        LineRatings ours = userTeam.flatMap((u) -> repository.findTeam(careerId, u).map((t) -> toLineRatings(t, repository.findMatchInfo(careerId, u)))).orElse(null);
        return new OpponentReport(opponent.teamId(), opponent.name(), opponent.leagueName(), formation, new Lines(ours, toLineRatings(opponent, info)),
                opponent.buildUpPlay(), opponent.defensiveDepth(), eleven,
                repository.findDangerousPlayers(careerId, teamId, properties.getDangerousPlayersLimit()), setPieces,
                repository.findCardRisk(careerId, teamId, properties.getCardRiskLimit()), missing);
    }

    private List<LikelyStarter> solveLikelyEleven(CareerEntity career, List<SquadMember> squad, FormationDefinition formation) {
        if (squad.size() < ELEVEN) {
            return List.of();
        }
        Map<Long, Integer> form = new HashMap<>();
        squad.forEach((m) -> form.put(m.playerId(), m.form()));
        List<FitPlayer> players = playerService.fitPlayers(squad.stream().map(SquadMember::playerId).toList(), career.getGameVersionId(), career.getId());
        Map<Long, FitPlayer> byId = players.stream().collect(Collectors.toMap(FitPlayer::getId, Function.identity(), (a, b) -> a));
        List<SlotAssignment> slots = squadFitService.solve(players, tacticResolver.resolve(TacticRequest.builder().formation(formation.id()).build()).slots(),
                fitProperties.getCareerEngineVersion(), true).getSlots();
        return slots.stream().filter((s) -> Objects.nonNull(s.getPlayerId()))
                .map((s) -> new LikelyStarter(s.getSlotId(), s.getPosition(), s.getPlayerId(), s.getPlayerName(), byId.get(s.getPlayerId()).getOverall(),
                        form.get(s.getPlayerId())))
                .toList();
    }

    private Optional<FormationDefinition> findClosestFormation(List<Integer> positions) {
        Map<String, Long> wanted = positions.stream().filter(Objects::nonNull).map(DumpMappings.FORMATION_POSITION_BY_CODE::get).filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        if (wanted.isEmpty()) {
            return Optional.empty();
        }
        return formationRegistry.findAll().stream().max(Comparator.comparingLong((FormationDefinition f) -> countOverlap(wanted, f))
                .thenComparing((FormationDefinition f) -> -f.id().length()));
    }

    private static long countOverlap(Map<String, Long> wanted, FormationDefinition formation) {
        Map<String, Long> have = formation.slots().stream().collect(Collectors.groupingBy(com.fcareer.tactics.model.FormationSlot::position, Collectors.counting()));
        return wanted.entrySet().stream().mapToLong((e) -> Math.min(e.getValue(), have.getOrDefault(e.getKey(), 0L))).sum();
    }

    private static Formation toFormation(TeamMatchInfo info, String matchedId) {
        List<FormationSlot> slots = new ArrayList<>();
        for (int i = 0; i < info.positions().size(); i++) {
            Integer code = info.positions().get(i);
            Integer role = Objects.isNull(info.roles()) || info.roles().size() <= i ? null : info.roles().get(i);
            slots.add(new FormationSlot(i, code, Objects.isNull(code) ? null : DumpMappings.FORMATION_POSITION_BY_CODE.get(code), role));
        }
        return new Formation(info.formationName(), matchedId, true, slots);
    }

    private SetPieces findSetPieces(TeamMatchInfo info) {
        Map<Long, PlayerRef> names = repository.findNames(Stream.of(info.captainId(), info.penaltyTakerId(), info.freeKickTakerId(), info.leftCornerTakerId(),
                info.rightCornerTakerId()).filter(Objects::nonNull).toList());
        Function<Long, PlayerRef> ref = (id) -> Objects.isNull(id) ? null : names.get(id);
        return new SetPieces(ref.apply(info.captainId()), ref.apply(info.penaltyTakerId()), ref.apply(info.freeKickTakerId()),
                ref.apply(info.leftCornerTakerId()), ref.apply(info.rightCornerTakerId()));
    }

    private static LineRatings toLineRatings(TeamRow team, Optional<TeamMatchInfo> info) {
        return new LineRatings(team.overall(), team.attack(), team.midfield(), team.defence(),
                info.map(TeamMatchInfo::matchdayOverall).orElse(null), info.map(TeamMatchInfo::matchdayAttack).orElse(null),
                info.map(TeamMatchInfo::matchdayMidfield).orElse(null), info.map(TeamMatchInfo::matchdayDefence).orElse(null));
    }
}
