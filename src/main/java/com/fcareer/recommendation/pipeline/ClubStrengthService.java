package com.fcareer.recommendation.pipeline;

import com.fcareer.catalog.repository.CareerPlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Kariyer başına kulüp/lig gücü; `career.data_version` değişince (içe aktarma, elle düzenleme) yeniden hesaplanır. */
@Service
@RequiredArgsConstructor
public class ClubStrengthService {

    private static final int MIN_CLUBS_PER_LEAGUE = 3;

    private record Cached(long dataVersion, ClubStrength strength) {
    }

    private final CareerPlayerRepository careerPlayerRepository;
    private final RecommendProperties properties;
    private final JdbcTemplate jdbc;
    private final Map<Long, Cached> cache = new ConcurrentHashMap<>();

    public ClubStrength of(long careerId) {
        Long version = jdbc.queryForObject("SELECT data_version FROM career WHERE id = ?", Long.class, careerId);
        long current = Objects.isNull(version) ? 0 : version;
        Cached cached = cache.get(careerId);
        if (Objects.nonNull(cached) && cached.dataVersion() == current) {
            return cached.strength();
        }
        ClubStrength fresh = ClubStrength.of(careerPlayerRepository.teamStrengths(careerId, properties.getClubStrengthTop()), properties.getMinClubPlayers(), MIN_CLUBS_PER_LEAGUE);
        cache.put(careerId, new Cached(current, fresh));
        return fresh;
    }
}
