package com.fcareer.career.match;

import com.fcareer.career.match.MatchModels.RatingRow;
import com.fcareer.career.match.MatchModels.SeasonRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchDataImporter {

    static final String RATING_TABLE = "career_playermatchratinghistory";
    static final String SEASON_TABLE = "player_season_stats";
    private static final int YEAR_FACTOR = 10_000;
    private static final int MONTH_FACTOR = 100;

    private final MatchDataRepository repository;

    public Summary apply(long careerId, Map<String, List<Map<String, String>>> tables, Map<Long, Long> eaToBase) {
        List<Map<String, String>> ratingRows = tables.get(RATING_TABLE);
        List<Map<String, String>> seasonRows = tables.get(SEASON_TABLE);
        int ratings = isEmpty(ratingRows) ? 0 : repository.upsertRatings(careerId, toRatings(ratingRows, eaToBase));
        List<SeasonRow> mappedSeasons = isEmpty(seasonRows) ? List.of() : toSeasons(seasonRows, eaToBase);
        int seasons = mappedSeasons.isEmpty() ? 0 : repository.replaceSeasonStats(careerId, mappedSeasons);
        log.info("Match data imported careerId : {} ratings : {} seasonRows : {} ratingTable : {} seasonTable : {}",
                careerId, ratings, seasons, !isEmpty(ratingRows), !isEmpty(seasonRows));
        return new Summary(ratings, seasons);
    }

    private static List<RatingRow> toRatings(List<Map<String, String>> rows, Map<Long, Long> eaToBase) {
        Map<String, RatingRow> unique = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            Long playerId = eaToBase.get(toLong(row.get("playerid")));
            LocalDate date = toDate(toInt(row.get("date")));
            Integer rating = toSmall(row.get("rating"));
            if (Objects.isNull(playerId) || Objects.isNull(date) || Objects.isNull(rating)) {
                continue;
            }
            unique.put(playerId + "|" + date, new RatingRow(playerId, date, toSmall(row.get("minsplayed")), toSmall(row.get("position")), rating));
        }
        return new ArrayList<>(unique.values());
    }

    private static List<SeasonRow> toSeasons(List<Map<String, String>> rows, Map<Long, Long> eaToBase) {
        Map<String, SeasonRow> unique = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            Long playerId = eaToBase.get(toLong(row.get("playerid")));
            Integer teamId = toInt(row.get("teamid"));
            Integer competitionId = toInt(row.get("compobjid"));
            if (Objects.isNull(playerId) || Objects.isNull(teamId) || Objects.isNull(competitionId)) {
                continue;
            }
            unique.put(playerId + "|" + teamId + "|" + competitionId, new SeasonRow(playerId, teamId, competitionId, count(row, "app"), count(row, "goals"),
                    count(row, "assists"), total(row, "avg"), count(row, "yellow"), count(row, "red"), count(row, "clean_sheets")));
        }
        return new ArrayList<>(unique.values());
    }

    private static LocalDate toDate(Integer yyyymmdd) {
        if (Objects.isNull(yyyymmdd)) {
            return null;
        }
        try {
            return LocalDate.of(yyyymmdd / YEAR_FACTOR, yyyymmdd / MONTH_FACTOR % MONTH_FACTOR, yyyymmdd % MONTH_FACTOR);
        } catch (DateTimeException e) {
            log.warn("Match rating date skipped value : {}", yyyymmdd);
            return null;
        }
    }

    private static int count(Map<String, String> row, String column) {
        Integer value = toInt(row.get(column));
        return Objects.isNull(value) || value < 0 ? 0 : Math.min(value, Short.MAX_VALUE);
    }

    private static int total(Map<String, String> row, String column) {
        Integer value = toInt(row.get(column));
        return Objects.isNull(value) || value < 0 ? 0 : value;
    }

    private static Integer toSmall(String value) {
        Long parsed = toLong(value);
        return Objects.isNull(parsed) || parsed < Short.MIN_VALUE || parsed > Short.MAX_VALUE ? null : parsed.intValue();
    }

    private static boolean isEmpty(List<Map<String, String>> rows) {
        return Objects.isNull(rows) || rows.isEmpty();
    }

    private static Integer toInt(String value) {
        Long parsed = toLong(value);
        return Objects.isNull(parsed) ? null : parsed.intValue();
    }

    private static Long toLong(String value) {
        if (Objects.isNull(value) || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public record Summary(int ratings, int seasonRows) {
    }
}
