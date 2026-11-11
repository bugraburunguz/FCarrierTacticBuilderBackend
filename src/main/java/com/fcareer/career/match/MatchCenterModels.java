package com.fcareer.career.match;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

public final class MatchCenterModels {

    private MatchCenterModels() {
    }

    public enum Missing { USER_TEAM, FORMATION, LIKELY_ELEVEN, FORM, SEASON_STATS, SET_PIECES }

    public record Opponent(int teamId, String name, String leagueName, Integer overall, boolean sameLeague, List<Integer> competitionIds) {
    }

    public record LineRatings(Integer overall, Integer attack, Integer midfield, Integer defence, Integer matchdayOverall, Integer matchdayAttack,
                              Integer matchdayMidfield, Integer matchdayDefence) {
    }

    public record Lines(LineRatings ours, LineRatings opponent) {
    }

    public record FormationSlot(int index, Integer positionCode, String position, Integer roleCode) {
    }

    public record Formation(String name, String matchedFormationId, boolean defaultFormation, List<FormationSlot> slots) {
    }

    public record LikelyStarter(String slotId, String position, long playerId, String name, Integer overall, Integer form) {
    }

    public record DangerousPlayer(long playerId, String name, Integer overall, Integer form, int appearances, int goals, int assists, Double averageRating) {
    }

    public record PlayerRef(long playerId, String name) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SetPieces(PlayerRef captain, PlayerRef penaltyTaker, PlayerRef freeKickTaker, PlayerRef leftCornerTaker, PlayerRef rightCornerTaker) {
    }

    public record CardRisk(long playerId, String name, int appearances, int yellowCards, int redCards) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OpponentReport(int teamId, String name, String leagueName, Formation formation, Lines lines, Integer buildUpPlay, Integer defensiveDepth,
                                 List<LikelyStarter> likelyEleven, List<DangerousPlayer> dangerousPlayers, SetPieces setPieces, List<CardRisk> cardRisk,
                                 List<Missing> missing) {
    }

    public record TeamMatchInfo(int teamId, String formationName, List<Integer> positions, List<Integer> roles, Long captainId, Long penaltyTakerId,
                                Long freeKickTakerId, Long leftCornerTakerId, Long rightCornerTakerId, Integer matchdayOverall, Integer matchdayAttack,
                                Integer matchdayMidfield, Integer matchdayDefence) {
    }

    record TeamRow(int teamId, String name, String leagueName, Integer overall, Integer attack, Integer midfield, Integer defence, Integer buildUpPlay,
                   Integer defensiveDepth) {
    }

    record SquadMember(long playerId, Integer form) {
    }
}
