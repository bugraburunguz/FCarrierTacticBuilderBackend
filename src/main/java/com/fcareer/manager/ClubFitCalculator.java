package com.fcareer.manager;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fcareer.advisor.service.FeasibilityService;
import com.fcareer.advisor.service.FormationRanker;
import com.fcareer.advisor.service.PositionAdviceService;
import com.fcareer.catalog.service.PlayerService;
import com.fcareer.common.cache.CacheStats;
import com.fcareer.common.cache.ReferenceCache;
import com.fcareer.fitengine.configuration.FitEngineProperties;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.response.SquadFitResponse;
import com.fcareer.fitengine.service.SquadFitService;
import com.fcareer.manager.ManagerModels.Style;
import com.fcareer.tactics.model.request.TacticRequest;
import com.fcareer.tactics.service.FormationRegistry;
import com.fcareer.tactics.service.TacticResolver;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.fcareer.common.constants.KeyConstants.CACHE_MANAGER_FIT;

/**
 * MGR-a madde 4 "Uyum": kariyer yokken bir katalog kulübünün kadrosuyla seçilen formasyon ve tarzda Takım gücü (başlangıç önerisi motoru: formasyon
 * "Fark etmez" ise kulübün en uygun formasyonu; dizilişi SquadFitService çözer). Sonuç (kulüp, formasyon, tarz, katalog sürümü) anahtarıyla önbellekte.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClubFitCalculator {

    private static final int MIN_PLAYERS = 11;
    private static final Duration TTL = Duration.ofHours(12);
    private static final String AUTO = "auto";
    private static final int STATS_LOG_EVERY = 200;
    private static final int MAX_FIT_THREADS = 4;

    private final PlayerService playerService;
    private final FeasibilityService feasibilityService;
    private final PositionAdviceService positionAdviceService;
    private final FormationRanker formationRanker;
    private final FormationRegistry formationRegistry;
    private final TacticResolver tacticResolver;
    private final SquadFitService squadFitService;
    private final FitEngineProperties fitProperties;
    private final ReferenceCache cache;
    private final ManagerRepository managerRepository;
    private final AtomicLong computations = new AtomicLong();
    private final CacheStats stats = new CacheStats("club-fit", STATS_LOG_EVERY);
    private final AtomicInteger threadNumber = new AtomicInteger();
    private final ExecutorService executor = Executors.newFixedThreadPool(Math.min(MAX_FIT_THREADS, Runtime.getRuntime().availableProcessors()), (runnable) -> {
        Thread thread = new Thread(runnable, "club-fit-" + threadNumber.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

    /** formation null → kulüp kadrosu 11'den azsa uyum yok. squadFit = Takım gücü (0–100); levelFit = ilk 11'in ortalama Rol oturması. */
    public record ClubFit(String formation, Double squadFit, Double levelFit) {
    }

    /** Gözlem/test: önbellek dışı hesap sayısı. */
    public long computations() {
        return computations.get();
    }

    public ClubFit fit(int clubId, short gameVersionId, String formation, Style style, String catalogVersion) {
        return fit(clubId, gameVersionId, formation, style, catalogVersion, null);
    }

    public ClubFit fit(int clubId, short gameVersionId, String formation, Style style, String catalogVersion, Long archivedSquadVersionId) {
        return fitAll(List.of(clubId), gameVersionId, formation, style, catalogVersion, archivedSquadVersionId).get(clubId);
    }

    public Map<Integer, ClubFit> fitAll(List<Integer> clubIds, short gameVersionId, String formation, Style style, String catalogVersion, Long archivedSquadVersionId) {
        Map<Integer, ClubFit> fits = new HashMap<>();
        List<Integer> missing = new ArrayList<>();
        for (Integer clubId : clubIds) {
            Optional<ClubFit> cached = cache.get(buildKey(clubId, gameVersionId, formation, style, catalogVersion, archivedSquadVersionId), new TypeReference<ClubFit>() { });
            stats.record(cached.isPresent());
            cached.ifPresentOrElse((fit) -> fits.put(clubId, fit), () -> missing.add(clubId));
        }
        if (missing.isEmpty()) {
            return fits;
        }
        Map<Integer, List<FitPlayer>> squads = findSquads(missing, gameVersionId, archivedSquadVersionId);
        List<CompletableFuture<ClubFit>> computed = missing.stream()
                .map((clubId) -> CompletableFuture.supplyAsync(() -> compute(squads.getOrDefault(clubId, List.of()), formation, style), executor)).toList();
        RuntimeException failure = null;
        for (int i = 0; i < missing.size(); i++) {
            try {
                ClubFit fit = join(computed.get(i));
                cache.put(buildKey(missing.get(i), gameVersionId, formation, style, catalogVersion, archivedSquadVersionId), fit, TTL);
                fits.put(missing.get(i), fit);
            } catch (RuntimeException e) {
                log.warn("Club fit failed clubId : {} reason : {}", missing.get(i), e.getMessage());
                failure = Objects.isNull(failure) ? e : failure;
                computed.forEach((f) -> f.cancel(false));
            }
        }
        if (Objects.nonNull(failure)) {
            throw failure;
        }
        return fits;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    private static ClubFit join(CompletableFuture<ClubFit> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw e;
        }
    }

    public CacheStats cacheStats() {
        return stats;
    }

    private static String buildKey(int clubId, short gameVersionId, String formation, Style style, String catalogVersion, Long archivedSquadVersionId) {
        return CACHE_MANAGER_FIT + gameVersionId + ":" + catalogVersion + ":" + Objects.requireNonNullElse(archivedSquadVersionId, 0L) + ":" + clubId + ":"
                + Objects.requireNonNullElse(formation, AUTO) + ":" + style;
    }

    private ClubFit compute(List<FitPlayer> players, String formation, Style style) {
        computations.incrementAndGet();
        if (players.size() < MIN_PLAYERS) {
            return new ClubFit(null, null, null);
        }
        String chosen = Objects.nonNull(formation) ? formation : best(players);
        if (Objects.isNull(chosen)) {
            return new ClubFit(null, null, null);
        }
        TacticRequest tactic = TacticRequest.builder().formation(chosen).setup(ManagerRules.setup(style)).build();
        SquadFitResponse solved = squadFitService.solveBest(players, tacticResolver.resolve(tactic).slots(), fitProperties.getCareerEngineVersion());
        Double level = Objects.isNull(solved.getSlots()) ? null : solved.getSlots().stream()
                .filter((s) -> Objects.nonNull(s.getRoleFit()) && Objects.nonNull(s.getRoleFit().getLevelFit()))
                .mapToDouble((s) -> s.getRoleFit().getLevelFit()).average().stream().boxed().findFirst().orElse(null);
        return new ClubFit(chosen, Math.round(solved.getSquadFit() * 10) / 10.0, Objects.isNull(level) ? null : Math.round(level * 10) / 10.0);
    }

    private Map<Integer, List<FitPlayer>> findSquads(List<Integer> clubIds, short gameVersionId, Long archivedSquadVersionId) {
        Map<Integer, List<Long>> squads = managerRepository.catalogSquads(clubIds, gameVersionId, archivedSquadVersionId);
        List<Long> playerIds = squads.values().stream().filter((ids) -> ids.size() >= MIN_PLAYERS).flatMap(List::stream).toList();
        if (playerIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, FitPlayer> players = playerService.fitCatalogPlayers(playerIds, gameVersionId, archivedSquadVersionId).stream()
                .collect(Collectors.toMap(FitPlayer::getId, Function.identity(), (a, b) -> a));
        Map<Integer, List<FitPlayer>> out = new HashMap<>();
        squads.forEach((clubId, ids) -> {
            if (ids.size() >= MIN_PLAYERS) {
                out.put(clubId, ids.stream().map(players::get).toList());
            }
        });
        return out;
    }

    private String best(List<FitPlayer> players) {
        FeasibilityService.SquadProfile profile = feasibilityService.profile(players, positionAdviceService.bestScores(players));
        List<FormationRanker.RankedFormation> ranked = formationRanker.rank(players, profile, formationRegistry.findAll());
        return ranked.isEmpty() ? null : ranked.get(0).formation().id();
    }

    /** Formasyon kimliği geçerli mi (profil doğrulaması). */
    public boolean knownFormation(String formation) {
        return formationRegistry.findAll().stream().anyMatch((f) -> f.id().equals(formation));
    }
}
