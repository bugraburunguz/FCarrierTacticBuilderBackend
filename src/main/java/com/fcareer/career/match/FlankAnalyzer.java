package com.fcareer.career.match;

import com.fcareer.career.match.MatchCenterModels.FlankMatchup;
import com.fcareer.career.match.MatchCenterModels.FlankPlayer;
import com.fcareer.career.match.MatchCenterModels.FlankSide;
import com.fcareer.career.match.MatchCenterModels.FlankThreat;
import com.fcareer.career.match.MatchCenterModels.FlankVerdict;
import com.fcareer.career.match.MatchCenterModels.Flanks;
import com.fcareer.career.match.MatchCenterModels.OurFlank;
import com.fcareer.career.match.MatchCenterModels.TacticalNote;
import com.fcareer.career.match.MatchCenterModels.TheirFlank;
import com.fcareer.common.service.MessageService;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.FitRole;
import com.fcareer.tacticanalysis.RoleMovements;
import com.fcareer.tacticanalysis.RoleMovements.Movement;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class FlankAnalyzer {

    private static final Set<String> LEFT_POSITIONS = Set.of("LB", "LWB", "LM", "LW");
    private static final Set<String> RIGHT_POSITIONS = Set.of("RB", "RWB", "RM", "RW");
    private static final Set<String> FULLBACK_POSITIONS = Set.of("LB", "LWB", "RB", "RWB");
    private static final int MAX_SCORE = 100;
    private static final int MIN_SCORE = 0;

    private static final String NOTE_PREFIX = "match.note.";
    private static final String SIDE_PREFIX = "match.flank.side.";
    private static final String OPPOSITE_PREFIX = "match.flank.opposite.";
    private static final String FULLBACK_FALLBACK = "match.flank.fullback";
    private static final String GOALS_ASSISTS = "match.flank.goals-assists";
    private static final String EXPOSED = "FLANK_EXPOSED";
    private static final String EXPOSED_COUNTER = "FLANK_EXPOSED_COUNTER";
    private static final String COUNTER = "FLANK_COUNTER";

    private final MatchProperties properties;
    private final RoleMovements movements;
    private final MessageService messageService;

    public record FlankSlot(String position, FitPlayer player, FitRole role, int goalsAssists) {
    }

    private record Scored(FlankSlot slot, int lean, int pace, int threat, int cover) {
    }

    public Optional<Flanks> analyze(List<FlankSlot> ours, List<FlankSlot> theirs) {
        if (ours.isEmpty() || theirs.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Flanks(buildMatchup(FlankSide.LEFT, ours, theirs), buildMatchup(FlankSide.RIGHT, ours, theirs)));
    }

    public List<TacticalNote> buildNotes(Flanks flanks) {
        return Stream.of(flanks.left(), flanks.right()).filter(Objects::nonNull).filter((m) -> m.verdict() != FlankVerdict.BALANCED).map(this::buildNote).toList();
    }

    private TacticalNote buildNote(FlankMatchup matchup) {
        String side = messageService.getMessage(SIDE_PREFIX + matchup.side().name());
        if (matchup.verdict() == FlankVerdict.OPPORTUNITY) {
            FlankPlayer fullback = findTheirForwardFullback(matchup);
            FlankPlayer runner = matchup.ours().players().stream().filter((p) -> !isFullback(p)).max(Comparator.comparingInt(FlankPlayer::pace)).orElseThrow();
            return note(COUNTER, side, fullback.name(), fullback.attackLean(), runner.name(), runner.pace());
        }
        FlankThreat threat = matchup.theirs().keyThreat();
        FlankPlayer attacker = findOurAttacker(matchup);
        String fullback = matchup.ours().players().stream().filter(FlankAnalyzer::isFullback).findFirst().map(FlankPlayer::name)
                .orElse(messageService.getMessage(FULLBACK_FALLBACK));
        String goalsAssists = threat.goalsAssists() > 0 ? messageService.getMessage(GOALS_ASSISTS, threat.goalsAssists()) : "";
        Object[] args = {side, messageService.getMessage(OPPOSITE_PREFIX + matchup.side().name()), threat.name(), threat.threat(), threat.pace(), goalsAssists,
                attacker.name(), attacker.role(), matchup.ours().defensiveCover(), fullback, matchup.counter() ? findTheirForwardFullback(matchup).name() : null};
        return note(matchup.counter() ? EXPOSED_COUNTER : EXPOSED, args);
    }

    private static FlankPlayer findOurAttacker(FlankMatchup matchup) {
        List<FlankPlayer> players = matchup.ours().players();
        return players.stream().filter((p) -> !isFullback(p)).max(Comparator.comparingInt(FlankPlayer::attackLean))
                .orElseGet(() -> players.stream().max(Comparator.comparingInt(FlankPlayer::attackLean)).orElseThrow());
    }

    private static FlankPlayer findTheirForwardFullback(FlankMatchup matchup) {
        return matchup.theirs().players().stream().filter(FlankAnalyzer::isFullback).max(Comparator.comparingInt(FlankPlayer::attackLean)).orElseThrow();
    }

    private static boolean isFullback(FlankPlayer player) {
        return FULLBACK_POSITIONS.contains(player.position());
    }

    private TacticalNote note(String code, Object... args) {
        return new TacticalNote(code, messageService.getMessage(NOTE_PREFIX + code, args));
    }

    private FlankMatchup buildMatchup(FlankSide side, List<FlankSlot> ours, List<FlankSlot> theirs) {
        Set<String> ourSide = side == FlankSide.LEFT ? LEFT_POSITIONS : RIGHT_POSITIONS;
        Set<String> theirSide = side == FlankSide.LEFT ? RIGHT_POSITIONS : LEFT_POSITIONS;
        List<Scored> ourPlayers = ours.stream().filter((s) -> ourSide.contains(s.position())).map((s) -> score(s, false)).toList();
        List<Scored> theirPlayers = theirs.stream().filter((s) -> theirSide.contains(s.position())).map((s) -> score(s, true)).toList();
        if (ourPlayers.isEmpty() || theirPlayers.isEmpty()) {
            return null;
        }
        OurFlank ourFlank = new OurFlank(toPlayers(ourPlayers), average(ourPlayers, Scored::lean), average(ourPlayers, Scored::cover));
        Scored key = theirPlayers.stream().max(Comparator.comparingInt(Scored::threat)).orElseThrow();
        TheirFlank theirFlank = new TheirFlank(toPlayers(theirPlayers), key.threat(), average(theirPlayers, Scored::lean),
                new FlankThreat(key.slot().player().getId(), key.slot().player().getName(), key.threat(), key.pace(), key.slot().goalsAssists()));
        boolean exposed = isExposed(ourFlank, theirFlank);
        boolean counter = hasCounterChance(ourPlayers, theirPlayers);
        FlankVerdict verdict = exposed ? FlankVerdict.EXPOSED : counter ? FlankVerdict.OPPORTUNITY : FlankVerdict.BALANCED;
        return new FlankMatchup(side, ourFlank, theirFlank, verdict, counter);
    }

    private boolean isExposed(OurFlank ours, TheirFlank theirs) {
        MatchProperties.Flank config = properties.getPlan().getFlank();
        return theirs.threat() >= config.getExposedThreatMin() && theirs.attackLean() >= config.getExposedTheirLeanMin()
                && ours.attackLean() >= config.getExposedOurLeanMin() && ours.defensiveCover() < config.getExposedCoverBelow();
    }

    private boolean hasCounterChance(List<Scored> ours, List<Scored> theirs) {
        MatchProperties.Flank config = properties.getPlan().getFlank();
        boolean fullbackForward = theirs.stream().anyMatch((s) -> FULLBACK_POSITIONS.contains(s.slot().position()) && s.lean() >= config.getCounterFullbackLeanMin());
        boolean fastWinger = ours.stream().anyMatch((s) -> !FULLBACK_POSITIONS.contains(s.slot().position()) && s.pace() >= config.getCounterPaceMin()
                && s.lean() >= config.getCounterWingerLeanMin());
        return fullbackForward && fastWinger;
    }

    private Scored score(FlankSlot slot, boolean opponent) {
        MatchProperties.Flank config = properties.getPlan().getFlank();
        Optional<Movement> movement = Optional.ofNullable(slot.role()).flatMap((r) -> movements.find(r.getId()));
        double roleLean = movement.map((m) -> MAX_SCORE * (config.getLeanRun() * m.r() + config.getLeanRunBehind() * m.runBehind()))
                .orElse((double) config.getNeutralLean());
        double lean = opponent ? (1 - config.getOpponentAttrLeanWeight()) * roleLean + config.getOpponentAttrLeanWeight() * calculateAttrLean(slot.player()) : roleLean;
        double duty = movement.map(Movement::d).orElse(config.getDefaultDuty());
        int pace = calculatePace(slot.player());
        return new Scored(slot, clamp(lean), pace, calculateThreat(slot, pace), clamp(calculateDefence(slot.player()) * duty));
    }

    private int calculateThreat(FlankSlot slot, int pace) {
        MatchProperties.Flank config = properties.getPlan().getFlank();
        FitPlayer player = slot.player();
        double attrs = config.getThreatPace() * pace + config.getThreatDribbling() * player.attr(Attr.DRIBBLING)
                + config.getThreatCrossing() * player.attr(Attr.CROSSING) + config.getThreatBallControl() * player.attr(Attr.BALL_CONTROL);
        double bonus = Math.min(slot.goalsAssists() * config.getThreatPerGoalAssist(), config.getThreatGoalAssistCap());
        return clamp(attrs + bonus);
    }

    private double calculateAttrLean(FitPlayer player) {
        MatchProperties.Flank config = properties.getPlan().getFlank();
        double attack = (player.attr(Attr.SPRINT_SPEED) + player.attr(Attr.CROSSING) + player.attr(Attr.DRIBBLING)) / 3.0;
        return config.getNeutralLean() + config.getAttrLeanScale() * (attack - calculateDefence(player));
    }

    private static double calculateDefence(FitPlayer player) {
        return (player.attr(Attr.DEF_AWARENESS) + player.attr(Attr.STANDING_TACKLE) + player.attr(Attr.INTERCEPTIONS)) / 3.0;
    }

    private static int calculatePace(FitPlayer player) {
        return (int) Math.round((player.attr(Attr.ACCELERATION) + player.attr(Attr.SPRINT_SPEED)) / 2.0);
    }

    private static List<FlankPlayer> toPlayers(List<Scored> scored) {
        return scored.stream().map((s) -> {
            FitRole role = s.slot().role();
            return new FlankPlayer(s.slot().player().getId(), s.slot().player().getName(), s.slot().position(), Objects.isNull(role) ? null : role.getId(),
                    Objects.isNull(role) ? null : role.getName(), Objects.isNull(role) ? null : role.getFocus(), s.lean(), s.pace());
        }).toList();
    }

    private static int average(List<Scored> scored, ToIntFunction<Scored> value) {
        return (int) Math.round(scored.stream().mapToInt(value).average().orElse(MIN_SCORE));
    }

    private static int clamp(double value) {
        return (int) Math.max(MIN_SCORE, Math.min(MAX_SCORE, Math.round(value)));
    }
}
