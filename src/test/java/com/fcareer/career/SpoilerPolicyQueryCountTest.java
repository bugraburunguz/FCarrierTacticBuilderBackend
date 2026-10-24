package com.fcareer.career;

import com.fcareer.career.desk.SpoilerPolicy;
import com.fcareer.catalog.model.response.PlayerSummaryResponse;
import com.fcareer.recommendation.model.response.RecommendationItem;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** REVIEW (Yüksek, performans): spoiler modu istek başına bir kez okunur; toplu maskeleme öğe başına career sorgusu atmaz (N+1). */
class SpoilerPolicyQueryCountTest {

    @Test
    void maskingManyItemsReadsTheSpoilerModeOnce() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForList(anyString(), anyMap(), eq(String.class))).thenReturn(List.of(SpoilerPolicy.SCOUT_RANGE));
        SpoilerPolicy policy = new SpoilerPolicy(jdbc);
        List<RecommendationItem> items = IntStream.range(0, 20).mapToObj(i -> {
            RecommendationItem item = new RecommendationItem();
            PlayerSummaryResponse player = new PlayerSummaryResponse();
            player.setPotential(80 + i % 5);
            item.setPlayer(player);
            return item;
        }).toList();

        policy.maskItems(7L, items);

        verify(jdbc, times(1)).queryForList(anyString(), any(Map.class), eq(String.class));
        assertThat(items).allSatisfy(i -> assertThat(i.getPlayer().getPotential()).isNull());
    }

    @Test
    void aPrecomputedModeMasksWithoutAQuery() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        SpoilerPolicy policy = new SpoilerPolicy(jdbc);
        PlayerSummaryResponse player = new PlayerSummaryResponse();
        player.setPotential(83);

        policy.mask(true, player);
        assertThat(policy.bandOrSame(true, 83)).isEqualTo(80);
        assertThat(policy.bandOrSame(false, 83)).isEqualTo(83);

        assertThat(player.getPotential()).isNull();
        assertThat(player.getPotentialRange()).isEqualTo("80–84");
        verify(jdbc, times(0)).queryForList(anyString(), any(Map.class), eq(String.class));
    }
}
