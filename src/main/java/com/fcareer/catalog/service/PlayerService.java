package com.fcareer.catalog.service;

import com.fcareer.catalog.configuration.CatalogProperties;
import com.fcareer.catalog.entity.BasePlayerEntity;
import com.fcareer.catalog.entity.PlayerRatingEntity;
import com.fcareer.catalog.entity.PlayerValuationEntity;
import com.fcareer.catalog.mapper.PlayerMapper;
import com.fcareer.catalog.model.enums.ValuationSource;
import com.fcareer.catalog.model.request.PlayerSearchRequest;
import com.fcareer.catalog.model.response.PlayerDetailResponse;
import com.fcareer.catalog.model.response.PlayerSummaryResponse;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.repository.PlayerRatingRepository;
import com.fcareer.catalog.repository.PlayerSearchRepository;
import com.fcareer.catalog.repository.PlayerValuationRepository;
import com.fcareer.common.exception.NotFoundException;
import com.fcareer.common.model.PageResponse;
import com.fcareer.fitengine.model.FitPlayer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.fcareer.common.constants.ApiConstants.DEFAULT_PAGE_SIZE;
import static com.fcareer.common.constants.ApiConstants.MAX_PAGE_SIZE;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlayerService {

    private static final String PLAYER = "player";

    private final CatalogProperties catalogProperties;
    private final PlayerSearchRepository playerSearchRepository;
    private final BasePlayerRepository basePlayerRepository;
    private final PlayerRatingRepository playerRatingRepository;
    private final PlayerValuationRepository playerValuationRepository;
    private final PlayerMapper playerMapper;

    public PageResponse<PlayerSummaryResponse> search(PlayerSearchRequest request) {
        request.setGameVersionId(resolveGameVersion(request.getGameVersionId()));
        request.setSize(request.getSize() <= 0 ? DEFAULT_PAGE_SIZE : Math.min(request.getSize(), MAX_PAGE_SIZE));
        request.setPage(Math.max(request.getPage(), 0));
        return PageResponse.<PlayerSummaryResponse>builder()
                .items(playerSearchRepository.search(request))
                .page(request.getPage())
                .size(request.getSize())
                .totalItems(playerSearchRepository.count(request))
                .build();
    }

    public PlayerDetailResponse detail(Long playerId, Short gameVersionId) {
        short gv = resolveGameVersion(gameVersionId);
        BasePlayerEntity base = basePlayerRepository.findById(playerId)
                .orElseThrow(() -> new NotFoundException(PLAYER, playerId));
        PlayerRatingEntity rating = playerRatingRepository.findByBasePlayerIdAndGameVersionId(playerId, gv)
                .orElseThrow(() -> new NotFoundException(PLAYER, playerId));
        PlayerSummaryResponse summary = summaryOf(playerId, gv);
        return playerMapper.toDetail(summary, base, rating);
    }

    public List<FitPlayer> fitPlayers(List<Long> playerIds, Short gameVersionId) {
        short gv = resolveGameVersion(gameVersionId);
        Map<Long, BasePlayerEntity> bases = basePlayerRepository.findAllById(playerIds).stream()
                .collect(Collectors.toMap(BasePlayerEntity::getId, Function.identity()));
        Map<Long, PlayerRatingEntity> ratings = playerRatingRepository.findByBasePlayerIdInAndGameVersionId(playerIds, gv)
                .stream().collect(Collectors.toMap(PlayerRatingEntity::getBasePlayerId, Function.identity()));
        Map<Long, PlayerValuationEntity> valuations = playerValuationRepository
                .findByBasePlayerIdInAndGameVersionIdAndSourceAndOwnerUserIdIsNull(playerIds, gv, ValuationSource.MODELED)
                .stream().collect(Collectors.toMap(PlayerValuationEntity::getBasePlayerId, Function.identity()));
        return playerIds.stream().map(id -> {
            if (!bases.containsKey(id) || !ratings.containsKey(id)) {
                throw new NotFoundException(PLAYER, id);
            }
            return playerMapper.toFitPlayer(bases.get(id), ratings.get(id), valuations.get(id));
        }).toList();
    }

    public Map<Long, PlayerSummaryResponse> summaries(List<Long> ids, Short gameVersionId) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        PlayerSearchRequest request = new PlayerSearchRequest();
        request.setGameVersionId(resolveGameVersion(gameVersionId));
        request.setIds(ids);
        request.setSize(ids.size());
        return playerSearchRepository.search(request).stream()
                .collect(Collectors.toMap(PlayerSummaryResponse::getId, Function.identity()));
    }

    private PlayerSummaryResponse summaryOf(Long playerId, short gameVersionId) {
        PlayerSearchRequest request = new PlayerSearchRequest();
        request.setGameVersionId(gameVersionId);
        request.setSize(1);
        request.setIdFilter(playerId);
        return playerSearchRepository.search(request).stream().findFirst()
                .orElseThrow(() -> new NotFoundException(PLAYER, playerId));
    }

    private short resolveGameVersion(Short requested) {
        return Objects.isNull(requested) ? catalogProperties.getDefaultGameVersion() : requested;
    }
}
