package com.fcareer.recommendation;

import com.fcareer.catalog.repository.CareerPlayerRepository;
import com.fcareer.recommendation.pipeline.ClubStrengthService;
import com.fcareer.recommendation.pipeline.RecommendProperties;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** REVIEW (Yüksek, bellek): kulüp gücü önbelleği sınırlı (LRU); her dokunulan kariyer için sonsuza kadar büyümez (tek örnek, 512 MB heap). */
class ClubStrengthCacheBoundTest {

    @Test
    void theCacheKeepsAtMostTheConfiguredNumberOfCareers() {
        CareerPlayerRepository repository = mock(CareerPlayerRepository.class);
        when(repository.teamStrengths(anyLong(), anyInt())).thenReturn(List.of());
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), anyLong())).thenReturn(1L);
        ClubStrengthService service = new ClubStrengthService(repository, new RecommendProperties(), jdbc);

        for (long careerId = 1; careerId <= ClubStrengthService.CACHE_SIZE + 50; careerId++) {
            service.of(careerId);
        }

        assertThat(service.cachedCareers()).isEqualTo(ClubStrengthService.CACHE_SIZE);
    }
}
