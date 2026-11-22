package com.fcareer.catalog.service;

import com.fcareer.catalog.configuration.CatalogProperties;
import com.fcareer.catalog.mapper.PlayerMapper;
import com.fcareer.catalog.model.request.PlayerSearchRequest;
import com.fcareer.catalog.model.response.PlayerSummaryResponse;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.repository.CareerPlayerRepository;
import com.fcareer.catalog.repository.PlayerRatingRepository;
import com.fcareer.catalog.repository.PlayerSearchRepository;
import com.fcareer.catalog.squad.SquadVersionService;
import com.fcareer.common.model.PageResponse;
import com.fcareer.versioning.service.VersioningService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.fcareer.common.constants.ApiConstants.MAX_PAGE_SIZE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerServiceSearchItemsTest {

    private final PlayerSearchRepository playerSearchRepository = mock(PlayerSearchRepository.class);
    private final SquadVersionService squadVersionService = mock(SquadVersionService.class);
    private final CatalogProperties catalogProperties = new CatalogProperties();
    private final PlayerService playerService = new PlayerService(catalogProperties, playerSearchRepository, mock(BasePlayerRepository.class),
            mock(PlayerRatingRepository.class), mock(CareerPlayerRepository.class), squadVersionService, mock(PlayerMapper.class), mock(VersioningService.class));

    @Test
    void searchItemsReturnsTheSamePageWithoutCountingTheMatches() {
        PlayerSummaryResponse row = new PlayerSummaryResponse();
        row.setId(9L);
        when(playerSearchRepository.search(any())).thenReturn(List.of(row));
        when(squadVersionService.findArchivedSquadVersionOfCareer(any())).thenReturn(Optional.of(1L));
        PlayerSearchRequest request = PlayerSearchRequest.builder().size(MAX_PAGE_SIZE * 2).page(-1).build();

        List<PlayerSummaryResponse> items = playerService.searchItems(request);

        assertThat(items).extracting(PlayerSummaryResponse::getId).containsExactly(9L);
        assertThat(request.getSize()).isEqualTo(MAX_PAGE_SIZE);
        assertThat(request.getPage()).isZero();
        assertThat(request.getGameVersionId()).isEqualTo(catalogProperties.getDefaultGameVersion());
        verify(playerSearchRepository, never()).count(any());
    }

    @Test
    void publicSearchStillCountsTheMatches() {
        when(playerSearchRepository.search(any())).thenReturn(List.of());
        when(playerSearchRepository.count(any())).thenReturn(42L);
        when(squadVersionService.findArchivedSquadVersionOfCareer(any())).thenReturn(Optional.of(1L));

        PageResponse<PlayerSummaryResponse> page = playerService.search(PlayerSearchRequest.builder().size(5).build());

        assertThat(page.getTotalItems()).isEqualTo(42L);
        verify(playerSearchRepository, times(1)).count(any());
    }
}
