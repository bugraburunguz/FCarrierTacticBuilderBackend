package com.fcareer.catalog.squad;

import com.fcareer.career.model.response.CareerSquadVersionResponse;
import com.fcareer.catalog.configuration.CatalogProperties;
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

    private final SquadVersionRepository squadVersionRepository;
    private final CatalogProperties catalogProperties;

    public List<SquadVersionResponse> list() {
        List<SquadVersion> versions = squadVersionRepository.findAllByGameVersion(catalogProperties.getDefaultGameVersion());
        return IntStream.range(0, versions.size())
                .mapToObj(i -> SquadVersionConverter.toResponse(versions.get(i), i == LATEST_INDEX))
                .toList();
    }

    public SquadVersion resolveForNewCareer(short gameVersionId, Long requestedId) {
        if (Objects.isNull(requestedId)) {
            return findLatest(gameVersionId);
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

    private SquadVersion findLatest(short gameVersionId) {
        return squadVersionRepository.findLatest(gameVersionId)
                .orElseThrow(() -> {
                    log.warn("Squad version missing gameVersionId : {}", gameVersionId);
                    return new NotFoundException(SQUAD_VERSION, gameVersionId);
                });
    }
}
