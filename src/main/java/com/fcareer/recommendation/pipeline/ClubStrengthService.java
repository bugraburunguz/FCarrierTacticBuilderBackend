package com.fcareer.recommendation.pipeline;

import com.fcareer.catalog.repository.CareerPlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Kariyer başına kulüp/lig gücü; `career.data_version` değişince (içe aktarma, elle düzenleme) yeniden hesaplanır. */
@Service
@RequiredArgsConstructor
public class ClubStrengthService {

    private static final int MIN_CLUBS_PER_LEAGUE = 3;
    public static final int CACHE_SIZE = 64;

    private record Cached(long dataVersion, ClubStrength strength) {
    }

    private final CareerPlayerRepository careerPlayerRepository;
    private final RecommendProperties properties;
    private final JdbcTemplate jdbc;
    // REVIEW (bellek): en son kullanılan CACHE_SIZE kariyer (girdi ~100-150 KB); eskiden her dokunulan kariyer için sınırsız büyüyordu.
    private final Map<Long, Cached> cache = Collections.synchronizedMap(new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Cached> eldest) {
            return size() > CACHE_SIZE;
        }
    });

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

    /** Test ve gözlem: önbellekteki kariyer sayısı. */
    public int cachedCareers() {
        return cache.size();
    }
}
