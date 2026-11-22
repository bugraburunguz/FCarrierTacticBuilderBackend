package com.fcareer.catalog.squad;

import com.fcareer.career.model.response.CareerSquadVersionResponse;
import com.fcareer.catalog.configuration.CatalogProperties;
import com.fcareer.catalog.entity.PlayerRatingEntity;
import com.fcareer.catalog.repository.PlayerRatingRepository;
import com.fcareer.common.exception.FcareerRuntimeException;
import com.fcareer.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;

import static com.fcareer.common.constants.ErrorCodes.VALIDATION_ERROR;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SquadVersionService {

    private static final String SQUAD_VERSION = "squad_version";
    private static final String UNKNOWN_SQUAD_VERSION = "Unknown squad version";
    private static final int LATEST_INDEX = 0;
    private static final String FIRST_VERSION_LABEL = "İlk kadro (Sürüm 1)";

    private final SquadVersionRepository squadVersionRepository;
    private final PlayerRatingRepository playerRatingRepository;
    private final CatalogProperties catalogProperties;

    public List<SquadVersionResponse> list() {
        List<SquadVersion> versions = squadVersionRepository.findAllByGameVersion(catalogProperties.getDefaultGameVersion());
        return IntStream.range(0, versions.size())
                .mapToObj(i -> SquadVersionConverter.toResponse(versions.get(i), i == LATEST_INDEX))
                .toList();
    }

    @Transactional
    public SquadVersion resolveForNewCareer(short gameVersionId, Long requestedId) {
        if (Objects.isNull(requestedId)) {
            return findOrCreateLatest(gameVersionId);
        }
        return squadVersionRepository.findById(requestedId)
                .filter(version -> version.gameVersionId() == gameVersionId)
                .orElseThrow(() -> {
                    log.warn("Squad version rejected squadVersionId : {} gameVersionId : {}", requestedId, gameVersionId);
                    return new FcareerRuntimeException(VALIDATION_ERROR, UNKNOWN_SQUAD_VERSION);
                });
    }

    public Map<Long, CareerSquadVersionResponse> findCareerVersions(Collection<Short> gameVersionIds) {
        Map<Long, CareerSquadVersionResponse> byId = new HashMap<>();
        for (Short gameVersionId : new LinkedHashSet<>(gameVersionIds)) {
            List<SquadVersion> versions = squadVersionRepository.findAllByGameVersion(gameVersionId);
            for (int i = 0; i < versions.size(); i++) {
                byId.put(versions.get(i).id(), SquadVersionConverter.toCareerResponse(versions.get(i), i == LATEST_INDEX));
            }
        }
        return byId;
    }

    public List<PlayerRatingEntity> findCatalogSquad(long squadVersionId, int clubId, short gameVersionId) {
        if (squadVersionRepository.isLatest(squadVersionId)) {
            return playerRatingRepository.findCatalogSquad(clubId, gameVersionId);
        }
        return squadVersionRepository.findArchivedCatalogSquad(clubId, squadVersionId);
    }

    public Optional<PlayerRatingEntity> findCatalogRating(long squadVersionId, long basePlayerId, short gameVersionId) {
        if (squadVersionRepository.isLatest(squadVersionId)) {
            return playerRatingRepository.findByBasePlayerIdAndGameVersionId(basePlayerId, gameVersionId);
        }
        return squadVersionRepository.findArchivedCatalogRating(basePlayerId, squadVersionId);
    }

    private SquadVersion findOrCreateLatest(short gameVersionId) {
        Optional<SquadVersion> latest = squadVersionRepository.findLatest(gameVersionId);
        if (latest.isPresent()) {
            return latest.get();
        }
        log.info("Squad version missing, creating the first gameVersionId : {}", gameVersionId);
        squadVersionRepository.createFirstIfMissing(gameVersionId, FIRST_VERSION_LABEL);
        return squadVersionRepository.findLatest(gameVersionId)
                .orElseThrow(() -> new NotFoundException(SQUAD_VERSION, gameVersionId));
    }
}
