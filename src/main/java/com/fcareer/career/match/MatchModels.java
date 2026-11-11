package com.fcareer.career.match;

import java.time.LocalDate;
import java.util.List;

public final class MatchModels {

    private MatchModels() {
    }

    public enum Metric {
        GOALS, ASSISTS, RATING;

        public static Metric parse(String value) {
            if (java.util.Objects.isNull(value) || value.isBlank()) {
                return GOALS;
            }
            return java.util.Arrays.stream(values()).filter((m) -> m.name().equalsIgnoreCase(value.trim())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("metric"));
        }
    }

    public record MatchRating(LocalDate date, Integer minutes, Integer position, int rating) {
    }

    public record SeasonLine(int teamId, String teamName, int competitionId, int appearances, int goals, int assists, Double averageRating,
                             int yellowCards, int redCards, int cleanSheets) {
    }

    public record PlayerMatches(List<MatchRating> ratings, List<SeasonLine> seasons) {
    }

    public record SquadForm(long playerId, String playerName, List<Integer> lastRatings, int appearances, int goals, int assists, Double averageRating) {
    }

    public record CompetitionSummary(int competitionId, int teams, int players, int goals) {
    }

    public record Leader(long playerId, String playerName, int teamId, String teamName, int appearances, int goals, int assists, Double averageRating) {
    }

    record RatingRow(long playerId, LocalDate date, Integer minutes, Integer position, int rating) {
    }

    record SeasonRow(long playerId, int teamId, int competitionId, int appearances, int goals, int assists, int ratingTotal, int yellowCards,
                     int redCards, int cleanSheets) {
    }
}
