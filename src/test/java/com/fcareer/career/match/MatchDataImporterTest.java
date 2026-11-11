package com.fcareer.career.match;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MatchDataImporterTest {

    @Test
    void aSeasonTableWithNoMappedPlayerKeepsTheStoredSnapshot() {
        MatchDataRepository repository = mock(MatchDataRepository.class);
        Map<String, List<Map<String, String>>> tables = Map.of(MatchDataImporter.SEASON_TABLE, List.of(
                Map.of("playerid", "123", "teamid", "327", "compobjid", "1512", "app", "3", "goals", "1", "assists", "0", "avg", "210",
                        "yellow", "0", "red", "0", "clean_sheets", "0")));

        MatchDataImporter.Summary summary = new MatchDataImporter(repository).apply(1L, tables, Map.of());

        verify(repository, never()).replaceSeasonStats(anyLong(), anyList());
        assertThat(summary.seasonRows()).isZero();
    }

    @Test
    void outOfRangeSmallValuesDoNotBreakTheImport() {
        MatchDataRepository repository = mock(MatchDataRepository.class);
        Map<String, List<Map<String, String>>> tables = Map.of(MatchDataImporter.RATING_TABLE, List.of(
                Map.of("playerid", "1", "date", "20270203", "minsplayed", "999999", "position", "28", "rating", "8"),
                Map.of("playerid", "1", "date", "20270210", "minsplayed", "90", "position", "28", "rating", "99999")));

        new MatchDataImporter(repository).apply(1L, tables, Map.of(1L, 10L));

        verify(repository).upsertRatings(1L, List.of(new MatchModels.RatingRow(10L, java.time.LocalDate.of(2027, 2, 3), null, 28, 8)));
    }
}
