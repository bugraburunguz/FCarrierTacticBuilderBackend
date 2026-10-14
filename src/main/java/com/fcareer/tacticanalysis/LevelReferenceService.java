package com.fcareer.tacticanalysis;

import com.fcareer.catalog.repository.CareerPlayerRepository;
import com.fcareer.tacticanalysis.LevelReferenceRepository.Median;
import com.fcareer.tacticanalysis.LevelReferenceRepository.Medians;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Seviye referansı (E3-7b): kariyerde kullanıcının ligindeki aynı mevki medyanı (`career.data_version` değişince yenilenir), kariyer dışında takımın ilk 11 ortalama OVR bandının katalog medyanı.
 * Yeterli örnek yoksa referans verilmez; kural mutlak eşiğe düşer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LevelReferenceService {

    public static final String CB_PACE_REF = "CB_PACE_REF";
    public static final String BUILDUP_SAFETY_REF = "BUILDUP_SAFETY_REF";
    public static final String AERIAL_REF = "AERIAL_REF";
    private static final String TEAM_KEY_PREFIX = "T:";

    /** refs boş olabilir; label: "EFL League Two" ya da "OVR 66-74 katalog bandı". */
    public record LevelRefs(Map<String, Double> refs, String label) {
        public static final LevelRefs NONE = new LevelRefs(Map.of(), null);
    }

    private record Cached(long dataVersion, LevelRefs refs) {
    }

    private final LevelReferenceRepository repository;
    private final CareerPlayerRepository careerPlayerRepository;
    private final TacticRules rules;
    private final TacticAnalysisProperties properties;
    private final JdbcTemplate jdbc;
    private final Map<Long, Cached> careerCache = new ConcurrentHashMap<>();
    private final Map<String, LevelRefs> catalogCache = new ConcurrentHashMap<>();

    public LevelRefs forCareer(long careerId, Collection<Long> ownedIds) {
        Long version = jdbc.queryForObject("SELECT data_version FROM career WHERE id = ?", Long.class, careerId);
        long current = Objects.isNull(version) ? 0 : version;
        Cached cached = careerCache.get(careerId);
        if (Objects.nonNull(cached) && cached.dataVersion() == current) {
            return cached.refs();
        }
        LevelRefs fresh = computeCareer(careerId, ownedIds);
        careerCache.put(careerId, new Cached(current, fresh));
        return fresh;
    }

    private LevelRefs computeCareer(long careerId, Collection<Long> ownedIds) {
        Optional<Integer> team = careerPlayerRepository.dominantTeamKey(careerId, ownedIds).filter((k) -> k.startsWith(TEAM_KEY_PREFIX))
                .map((k) -> Integer.parseInt(k.substring(TEAM_KEY_PREFIX.length())));
        Optional<String> league = team.flatMap((t) -> repository.leagueOfTeam(careerId, t));
        if (league.isEmpty()) {
            return LevelRefs.NONE;
        }
        TacticParams p = rules.params();
        return pack(repository.leagueMedians(careerId, league.get(), p.heightMin(), p.heightMax()), league.get());
    }

    /** Kariyer dışı: avgOvr = atanan oyuncuların ortalama OVR'si. */
    public LevelRefs forCatalog(short gameVersionId, double avgOvr) {
        int lo = (int) Math.round(avgOvr) - properties.getCatalogOvrBand();
        int hi = (int) Math.round(avgOvr) + properties.getCatalogOvrBand();
        return catalogCache.computeIfAbsent(gameVersionId + ":" + lo, (k) -> {
            TacticParams p = rules.params();
            return pack(repository.catalogMedians(gameVersionId, lo, hi, p.heightMin(), p.heightMax()), "OVR " + lo + "-" + hi + " katalog bandı");
        });
    }

    private LevelRefs pack(Medians m, String label) {
        Map<String, Double> refs = new HashMap<>();
        put(refs, CB_PACE_REF, m.cbPace());
        put(refs, BUILDUP_SAFETY_REF, m.passerSafety());
        put(refs, AERIAL_REF, m.aerial());
        log.info("Tactic level refs label : {} refs : {}", label, refs.keySet());
        return refs.isEmpty() ? LevelRefs.NONE : new LevelRefs(refs, label);
    }

    private void put(Map<String, Double> refs, String key, Median m) {
        if (Objects.nonNull(m.value()) && m.n() >= properties.getLevelMinSample()) {
            refs.put(key, Math.round(m.value() * 10.0) / 10.0);
        }
    }
}
