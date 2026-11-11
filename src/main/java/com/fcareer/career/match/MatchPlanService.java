package com.fcareer.career.match;

import com.fcareer.career.entity.CareerEntity;
import com.fcareer.career.match.MatchCenterModels.AerialPlayer;
import com.fcareer.career.match.MatchCenterModels.CardRisk;
import com.fcareer.career.match.MatchCenterModels.FormationSource;
import com.fcareer.career.match.MatchCenterModels.LineRatings;
import com.fcareer.career.match.MatchCenterModels.MatchPlan;
import com.fcareer.career.match.MatchCenterModels.Missing;
import com.fcareer.career.match.MatchCenterModels.OpponentReport;
import com.fcareer.career.match.MatchCenterModels.OurFormation;
import com.fcareer.career.match.MatchCenterModels.OurStarter;
import com.fcareer.career.match.MatchCenterModels.PlayerRef;
import com.fcareer.career.match.MatchCenterModels.RecentForm;
import com.fcareer.career.match.MatchCenterModels.RestCandidate;
import com.fcareer.career.match.MatchCenterModels.SetPieceMatchup;
import com.fcareer.career.match.MatchCenterModels.TacticalNote;
import com.fcareer.career.match.MatchCenterModels.TeamMatchInfo;
import com.fcareer.career.service.CareerService;
import com.fcareer.catalog.service.PlayerService;
import com.fcareer.common.service.MessageService;
import com.fcareer.fitengine.configuration.FitEngineProperties;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.response.SlotAssignment;
import com.fcareer.fitengine.service.SquadFitService;
import com.fcareer.tactics.model.request.SlotSelection;
import com.fcareer.tactics.model.request.TacticRequest;
import com.fcareer.tactics.service.TacticResolver;
import com.fcareer.usertactic.UserTacticService;
import com.fcareer.usertactic.model.UserTacticModels.UserTactic;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MatchPlanService {

    private static final String NOTE_PREFIX = "match.note.";
    private static final String SLOT = "slot";
    private static final String ROLE_ID = "roleId";
    private static final String TAGS = "tags";

    private final CareerService careerService;
    private final MatchCenterService matchCenterService;
    private final MatchCenterRepository repository;
    private final MatchProperties properties;
    private final UserTacticService userTacticService;
    private final TacticResolver tacticResolver;
    private final PlayerService playerService;
    private final SquadFitService squadFitService;
    private final FitEngineProperties fitProperties;
    private final MessageService messageService;

    @Transactional(readOnly = true)
    public MatchPlan buildPlan(Long userId, Long careerId, Integer teamId) {
        OpponentReport report = matchCenterService.buildReport(userId, careerId, teamId);
        CareerEntity career = careerService.requireOwned(userId, careerId);
        MatchProperties.Plan config = properties.getPlan();
        List<Missing> missing = new ArrayList<>();
        Optional<Integer> userTeam = repository.findUserTeam(careerId);
        if (userTeam.isEmpty()) {
            missing.add(Missing.USER_TEAM);
        }
        List<Long> squad = careerService.squadPlayerIds(userId, careerId).playerIds();
        if (!repository.hasMatchRatings(careerId, squad)) {
            missing.add(Missing.MATCH_RATINGS);
        }
        List<RestCandidate> rest = repository.findRestCandidates(careerId, squad, config.getRestWindowDays(), config.getRestMinMatches(), config.getRestMinMinutes());
        Map<Long, RestCandidate> resting = rest.stream().collect(Collectors.toMap(RestCandidate::playerId, Function.identity()));
        Optional<OurPlan> ours = findOurTactic(userId, careerId, userTeam).map((t) -> solveOurEleven(career, squad, resting.keySet(), t));
        if (ours.isEmpty()) {
            missing.add(Missing.OUR_FORMATION);
        }
        List<OurStarter> eleven = ours.map(OurPlan::starters).orElse(List.of());
        Optional<TeamMatchInfo> ourInfo = userTeam.flatMap((u) -> repository.findMatchInfo(careerId, u));
        SetPieceMatchup setPieces = buildSetPieces(career, ours.map(OurPlan::players).orElse(List.of()), report, ourInfo);
        List<TacticalNote> notes = buildNotes(report, eleven, ours.map(OurPlan::backups).orElse(Map.of()), resting);
        return new MatchPlan(report.teamId(), ours.map(OurPlan::formation).orElse(null), eleven, rest, notes, setPieces, missing);
    }

    private Optional<OurTactic> findOurTactic(Long userId, Long careerId, Optional<Integer> userTeam) {
        UserTactic main = userTacticService.main(userId, careerId);
        if (Objects.nonNull(main) && Objects.nonNull(main.formation())) {
            List<SlotSelection> slots = Objects.isNull(main.slots()) ? List.of() : main.slots().stream().map(MatchPlanService::toSelection).toList();
            return Optional.of(new OurTactic(new OurFormation(main.formation(), FormationSource.MAIN_TACTIC),
                    TacticRequest.builder().formation(main.formation()).slots(new ArrayList<>(slots)).build()));
        }
        return userTeam.flatMap((u) -> repository.findMatchInfo(careerId, u)).map(TeamMatchInfo::positions).flatMap(matchCenterService::findClosestFormation)
                .map((f) -> new OurTactic(new OurFormation(f.id(), FormationSource.TEAM_DEFAULT), TacticRequest.builder().formation(f.id()).build()));
    }

    @SuppressWarnings("unchecked")
    private static SlotSelection toSelection(Map<String, Object> slot) {
        SlotSelection selection = new SlotSelection();
        selection.setSlotId((String) slot.get(SLOT));
        selection.setRoleId((String) slot.get(ROLE_ID));
        Object tags = slot.get(TAGS);
        selection.setTags(tags instanceof List<?> list ? new ArrayList<>((List<String>) list) : new ArrayList<>());
        return selection;
    }

    private OurPlan solveOurEleven(CareerEntity career, List<Long> squad, Set<Long> resting, OurTactic tactic) {
        List<FitPlayer> players = playerService.fitPlayers(squad, career.getGameVersionId(), career.getId());
        List<SlotAssignment> slots = squadFitService.solve(players, tacticResolver.resolve(tactic.request()).slots(), fitProperties.getCareerEngineVersion(), true)
                .getSlots();
        Map<Long, FitPlayer> byId = players.stream().collect(Collectors.toMap(FitPlayer::getId, Function.identity(), (a, b) -> a));
        List<Long> placed = slots.stream().map(SlotAssignment::getPlayerId).filter(Objects::nonNull).toList();
        Map<Long, RecentForm> recent = repository.findRecentForm(career.getId(), placed, properties.getRecentRatings()).stream()
                .collect(Collectors.toMap(RecentForm::playerId, Function.identity()));
        List<OurStarter> starters = slots.stream().filter((s) -> Objects.nonNull(s.getPlayerId())).map((s) -> {
            RecentForm form = recent.get(s.getPlayerId());
            return new OurStarter(s.getSlotId(), s.getPosition(), s.getPlayerId(), s.getPlayerName(), byId.get(s.getPlayerId()).getOverall(), s.getCombined(),
                    Objects.isNull(form) ? List.of() : form.ratings(), Objects.isNull(form) ? null : form.average(), resting.contains(s.getPlayerId()));
        }).toList();
        Map<Long, String> backups = slots.stream().filter((s) -> Objects.nonNull(s.getPlayerId()) && Objects.nonNull(s.getDepth()) && !s.getDepth().isEmpty())
                .collect(Collectors.toMap(SlotAssignment::getPlayerId, (s) -> s.getDepth().get(0).getPlayerName(), (a, b) -> a));
        return new OurPlan(tactic.formation(), starters, placed.stream().map(byId::get).toList(), backups);
    }

    private List<TacticalNote> buildNotes(OpponentReport report, List<OurStarter> eleven, Map<Long, String> backups, Map<Long, RestCandidate> resting) {
        MatchProperties.Plan config = properties.getPlan();
        List<TacticalNote> notes = new ArrayList<>();
        report.dangerousPlayers().stream().filter((d) -> d.goals() + d.assists() >= config.getKeyThreatMin()).findFirst()
                .ifPresent((d) -> notes.add(note("KEY_THREAT", d.name(), d.goals(), d.assists())));
        eleven.stream().filter(OurStarter::restSuggested).findFirst().ifPresent((s) -> {
            RestCandidate load = resting.get(s.playerId());
            notes.add(backups.containsKey(s.playerId())
                    ? note("ROTATE", s.name(), config.getRestWindowDays(), load.matches(), load.minutes(), backups.get(s.playerId()))
                    : note("ROTATE_NO_BACKUP", s.name(), config.getRestWindowDays(), load.matches(), load.minutes()));
        });
        LineRatings ours = Objects.isNull(report.lines()) ? null : report.lines().ours();
        LineRatings theirs = Objects.isNull(report.lines()) ? null : report.lines().opponent();
        if (Objects.nonNull(ours) && Objects.nonNull(theirs)) {
            int ourAttack = line(ours.matchdayAttack(), ours.attack());
            int ourMidfield = line(ours.matchdayMidfield(), ours.midfield());
            int ourDefence = line(ours.matchdayDefence(), ours.defence());
            int theirAttack = line(theirs.matchdayAttack(), theirs.attack());
            int theirMidfield = line(theirs.matchdayMidfield(), theirs.midfield());
            int theirDefence = line(theirs.matchdayDefence(), theirs.defence());
            if (theirAttack - ourDefence >= config.getLineGap()) {
                notes.add(note("OPP_ATTACK_STRONG", theirAttack, ourDefence));
            }
            if (ourAttack - theirDefence >= config.getLineGap()) {
                notes.add(note("OPP_DEFENCE_WEAK", theirDefence, ourAttack));
            }
            if (theirMidfield - ourMidfield >= config.getLineGap()) {
                notes.add(note("MIDFIELD_OUTNUMBERED", ourMidfield, theirMidfield));
            } else if (ourMidfield - theirMidfield >= config.getLineGap()) {
                notes.add(note("MIDFIELD_EDGE", ourMidfield, theirMidfield));
            }
        }
        if (Objects.nonNull(report.defensiveDepth()) && report.defensiveDepth() >= config.getHighLineFrom()) {
            notes.add(note("HIGH_LINE", report.defensiveDepth()));
        } else if (Objects.nonNull(report.defensiveDepth()) && report.defensiveDepth() <= config.getLowBlockTo()) {
            notes.add(note("LOW_BLOCK", report.defensiveDepth()));
        }
        eleven.stream().filter((s) -> Objects.nonNull(s.recentAverage()) && s.lastRatings().size() >= config.getRecentMinMatches()
                        && s.recentAverage() < config.getLowFormBelow())
                .min(Comparator.comparingDouble(OurStarter::recentAverage))
                .ifPresent((s) -> notes.add(backups.containsKey(s.playerId())
                        ? note("LOW_FORM", s.name(), s.lastRatings().size(), oneDecimal(s.recentAverage()), backups.get(s.playerId()))
                        : note("LOW_FORM_NO_BACKUP", s.name(), s.lastRatings().size(), oneDecimal(s.recentAverage()))));
        report.cardRisk().stream().filter((c) -> c.yellowCards() >= config.getCardYellowMin()).max(Comparator.comparingInt(CardRisk::yellowCards))
                .ifPresent((c) -> notes.add(note("CARD_PRONE", c.name(), c.yellowCards())));
        return notes.stream().limit(config.getMaxNotes()).toList();
    }

    private SetPieceMatchup buildSetPieces(CareerEntity career, List<FitPlayer> ourPlayers, OpponentReport report, Optional<TeamMatchInfo> ourInfo) {
        List<Long> theirIds = report.likelyEleven().stream().map(MatchCenterModels.LikelyStarter::playerId).toList();
        List<FitPlayer> theirPlayers = theirIds.isEmpty() ? List.of() : playerService.fitPlayers(theirIds, career.getGameVersionId(), career.getId());
        PlayerRef ourCorner = ourInfo.map(TeamMatchInfo::leftCornerTakerId).map((id) -> repository.findNames(List.of(id)).get(id)).orElse(null);
        PlayerRef theirCorner = Objects.isNull(report.setPieces()) ? null
                : Optional.ofNullable(report.setPieces().leftCornerTaker()).orElse(report.setPieces().rightCornerTaker());
        PlayerRef theirFreeKick = Objects.isNull(report.setPieces()) ? null : report.setPieces().freeKickTaker();
        return new SetPieceMatchup(rankAerial(ourPlayers), rankAerial(theirPlayers), ourCorner, theirCorner, theirFreeKick);
    }

    private List<AerialPlayer> rankAerial(List<FitPlayer> players) {
        MatchProperties.Plan config = properties.getPlan();
        return players.stream().filter(Objects::nonNull).filter((p) -> !p.getPositions().contains("GK"))
                .map((p) -> new AerialPlayer(p.getId(), p.getName(), aerialScore(p, config)))
                .sorted(Comparator.comparingInt(AerialPlayer::score).reversed().thenComparingLong(AerialPlayer::playerId))
                .limit(config.getAerialTop()).toList();
    }

    private static int aerialScore(FitPlayer player, MatchProperties.Plan config) {
        Map<Attr, Integer> attrs = player.getAttrs();
        double base = config.getAerialHeading() * attrs.getOrDefault(Attr.HEADING, 0) + config.getAerialJumping() * attrs.getOrDefault(Attr.JUMPING, 0)
                + config.getAerialStrength() * attrs.getOrDefault(Attr.STRENGTH, 0);
        int height = Objects.isNull(player.getHeightCm()) ? config.getAerialHeightBaseCm() : player.getHeightCm();
        return (int) Math.round(base + Math.max(0, height - config.getAerialHeightBaseCm()) * config.getAerialPerCm());
    }

    private static String oneDecimal(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static int line(Integer matchday, Integer base) {
        return Objects.nonNull(matchday) ? matchday : Objects.isNull(base) ? 0 : base;
    }

    private TacticalNote note(String code, Object... args) {
        return new TacticalNote(code, messageService.getMessage(NOTE_PREFIX + code, args));
    }

    private record OurTactic(OurFormation formation, TacticRequest request) {
    }

    private record OurPlan(OurFormation formation, List<OurStarter> starters, List<FitPlayer> players, Map<Long, String> backups) {
    }
}
