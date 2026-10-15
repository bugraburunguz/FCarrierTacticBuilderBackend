package com.fcareer.recommendation.pipeline;

import com.fcareer.career.service.CareerService;
import com.fcareer.catalog.model.request.PlayerSearchRequest;
import com.fcareer.catalog.model.response.PlayerSummaryResponse;
import com.fcareer.catalog.repository.CareerPlayerRepository;
import com.fcareer.catalog.repository.PlayerSearchRepository;
import com.fcareer.catalog.service.PlayerService;
import com.fcareer.common.exception.InvalidTacticException;
import com.fcareer.common.exception.NotFoundException;
import com.fcareer.common.model.PageResponse;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.FitRole;
import com.fcareer.recommendation.pipeline.model.CareerPlayerContractResponse;
import com.fcareer.fitengine.norms.PosDist;
import com.fcareer.fitengine.service.FitEngineV3;
import com.fcareer.roles.service.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.fcareer.common.constants.ApiConstants.DEFAULT_PAGE_SIZE;
import static com.fcareer.common.constants.ApiConstants.MAX_PAGE_SIZE;

/**
 * V3-06a: kariyer oyuncu listesi (kayıt dosyası havuzu). Ulaşılabilirlik kovası öneri hattının sınıflandırıcısıyla (FeasibilityInputs →
 * FeasibilityBuckets) her satıra eklenir; kova süzgeci ve rol sıralaması (FitEngineV3, yeni puanlama yok) tüm süzülmüş havuz üzerinde
 * Java'da hesaplanır ve kariyer veri sürümüne bağlı kısa ömürlü önbellekte tutulur ("daha fazla yükle" her sayfada yeniden hesaplamaz).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CareerPlayerList {

    /** FC mevkileri: rol sıralamasında havuz, rolün mevkisinde oynayabilenlerle (öneri hattının eşiği) sınırlanır. */
    private static final List<String> POSITIONS = List.of("GK", "RB", "RWB", "CB", "LB", "LWB", "CDM", "CM", "CAM", "RM", "LM", "RW", "LW", "CF", "ST");
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final int CACHE_SIZE = 32;

    private final PlayerService playerService;
    private final PlayerSearchRepository playerSearchRepository;
    private final CareerPlayerRepository careerPlayerRepository;
    private final CareerService careerService;
    private final FeasibilityInputs feasibilityInputs;
    private final RoleService roleService;
    private final FitEngineV3 engine;
    private final PosDist posDist;
    private final RecommendProperties properties;
    private final Clock clock;

    private record Ranked(long id, FeasibilityBucket bucket, Double roleFit) {
    }

    private record Cached(Instant at, List<Ranked> rows) {
    }

    private final Map<String, Cached> cache = new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    /** V3-06c: lig adı ve lig gücü "Lig seviyesi" göstergesi içindir (web eşiği tek yerde); bilinmiyorsa alan yok. */
    public CareerPlayerRepository.CareerFacets facets(Long userId, Long careerId) {
        CareerService.CareerSquad squad = careerService.squadPlayerIds(userId, careerId);
        CareerPlayerRepository.CareerFacets facets = careerPlayerRepository.careerFacets(careerId);
        return feasibilityInputs.leagueStrength(squad).map((l) -> facets.withLeague(l.league(), l.ovr())).orElse(facets);
    }

    /**
     * V3-06b: oyuncu sayfasının sözleşme ve değer verisi. Kova öneri hattının sınıflandırıcısından; kadrodaki oyuncu için kova ve alım
     * sonrası bütçe yok. Kayıtta olmayan alan null kalır (ENG-02).
     */
    public CareerPlayerContractResponse contract(Long userId, Long careerId, Long playerId) {
        CareerService.CareerSquad squad = careerService.squadPlayerIds(userId, careerId);
        PlayerSummaryResponse summary = playerService.summary(playerId, squad.gameVersionId(), careerId);
        CareerPlayerRepository.Contract contract = careerPlayerRepository.contract(careerId, playerId).orElse(null);
        boolean inSquad = squad.ownedIds().contains(playerId);
        FeasibilityInputs.Context context = context(squad);
        String feasibility = inSquad ? null : feasibilityInputs.classify(feasibilityInputs.derive(context, summary.getOverall(), summary.getValueEur(), summary.isFreeAgent(),
                careerPlayerRepository.extras(careerId, List.of(playerId)).get(playerId))).bucket().name();
        LocalDate contractUntil = Objects.isNull(contract) ? null : contract.contractUntil();
        Long afterPurchase = !inSquad && Objects.nonNull(squad.budgetEur()) && Objects.nonNull(summary.getValueEur()) ? squad.budgetEur() - summary.getValueEur() : null;
        return CareerPlayerContractResponse.builder()
                .inSquad(inSquad)
                .valueEur(summary.getValueEur()).valuationEstimated(summary.isValuationEstimated())
                .wageEur(Objects.isNull(contract) ? null : contract.wageEur())
                .releaseClauseEur(Objects.isNull(contract) ? null : contract.releaseClauseEur())
                .contractUntil(contractUntil).gameDate(context.gameDate())
                .monthsLeft(FeasibilityInputs.monthsLeft(context.gameDate(), contractUntil))
                .onLoan(Objects.nonNull(contract) && contract.onLoan())
                .feasibility(feasibility)
                .budgetEur(squad.budgetEur()).budgetAfterPurchaseEur(afterPurchase)
                .build();
    }

    public PageResponse<PlayerSummaryResponse> search(Long userId, PlayerSearchRequest request, List<String> bucketNames, String roleId, String rolePosition) {
        List<FeasibilityBucket> buckets = parseBuckets(bucketNames);
        CareerService.CareerSquad squad = careerService.squadPlayerIds(userId, request.getCareerId());
        if (buckets.isEmpty() && Objects.isNull(roleId)) {
            PageResponse<PlayerSummaryResponse> page = playerService.search(request);
            annotate(squad, page.getItems());
            return page;
        }
        FitRole role = Objects.isNull(roleId) ? null : resolveRole(roleId);
        String position = Objects.isNull(role) ? null : positionFor(role, rolePosition);
        if (Objects.nonNull(role)) {
            request.setPositions(compatible(position, request.getPositions()));
        }
        List<Ranked> ranked = ranked(squad, request, buckets, role, position);
        int size = request.getSize() <= 0 ? DEFAULT_PAGE_SIZE : Math.min(request.getSize(), MAX_PAGE_SIZE);
        int from = Math.min(Math.max(request.getPage(), 0) * size, ranked.size());
        List<Ranked> slice = ranked.subList(from, Math.min(from + size, ranked.size()));
        return PageResponse.<PlayerSummaryResponse>builder().items(rows(request, slice)).page(Math.max(request.getPage(), 0)).size(size).totalItems(ranked.size()).build();
    }

    private List<Ranked> ranked(CareerService.CareerSquad squad, PlayerSearchRequest request, List<FeasibilityBucket> buckets, FitRole role, String position) {
        String key = squad.careerId() + ":" + careerPlayerRepository.careerDataVersion(squad.careerId()) + ":" + signature(request) + ":" + buckets + ":"
                + (Objects.isNull(role) ? "" : role.getId() + "@" + position);
        synchronized (cache) {
            Cached hit = cache.get(key);
            if (Objects.nonNull(hit) && hit.at().plus(CACHE_TTL).isAfter(clock.instant())) {
                return hit.rows();
            }
        }
        long started = System.nanoTime();
        List<PlayerSearchRepository.Candidate> candidates = playerSearchRepository.candidates(request);
        FeasibilityInputs.Context context = context(squad);
        Map<Long, CareerPlayerRepository.Extra> extras = careerPlayerRepository.extras(squad.careerId(), candidates.stream().map(PlayerSearchRepository.Candidate::id).toList());
        List<Ranked> rows = new ArrayList<>();
        for (PlayerSearchRepository.Candidate c : candidates) {
            FeasibilityBucket bucket = feasibilityInputs.classify(feasibilityInputs.derive(context, c.overall(), c.valueEur(), c.freeAgent(), extras.get(c.id()))).bucket();
            if (buckets.isEmpty() || buckets.contains(bucket)) {
                rows.add(new Ranked(c.id(), bucket, null));
            }
        }
        if (Objects.nonNull(role) && !rows.isEmpty()) {
            Map<Long, FitPlayer> players = playerService.fitPlayers(rows.stream().map(Ranked::id).toList(), squad.gameVersionId(), squad.careerId()).stream()
                    .collect(Collectors.toMap(FitPlayer::getId, Function.identity(), (a, b) -> a));
            rows = rows.stream().filter((r) -> players.containsKey(r.id()))
                    .map((r) -> new Ranked(r.id(), r.bucket(), round1(engine.currentFit(players.get(r.id()), position, role).currentFit())))
                    .sorted(Comparator.comparing(Ranked::roleFit).reversed().thenComparingLong(Ranked::id)).toList();
        }
        log.info("Career player list ranked careerId : {} candidates : {} kept : {} role : {} ms : {}", squad.careerId(), candidates.size(), rows.size(),
                Objects.isNull(role) ? "-" : role.getId(), (System.nanoTime() - started) / 1_000_000);
        synchronized (cache) {
            cache.put(key, new Cached(clock.instant(), rows));
        }
        return rows;
    }

    /** Sayfanın tam satırları (aynı süzgeçler + id listesi), sıralı dilimin sırasıyla; kova ve rol uyumu eklenir. */
    private List<PlayerSummaryResponse> rows(PlayerSearchRequest request, List<Ranked> slice) {
        if (slice.isEmpty()) {
            return List.of();
        }
        request.setIds(slice.stream().map(Ranked::id).toList());
        request.setPage(0);
        request.setSize(slice.size());
        Map<Long, PlayerSummaryResponse> byId = playerService.search(request).getItems().stream()
                .collect(Collectors.toMap(PlayerSummaryResponse::getId, Function.identity(), (a, b) -> a));
        List<PlayerSummaryResponse> out = new ArrayList<>();
        for (Ranked r : slice) {
            PlayerSummaryResponse row = byId.get(r.id());
            if (Objects.nonNull(row)) {
                row.setFeasibility(r.bucket().name());
                row.setRoleFit(r.roleFit());
                out.add(row);
            }
        }
        return out;
    }

    /** Kova süzgeci ve rol yokken: yalnız sayfadaki satırlar sınıflandırılır (ucuz). */
    private void annotate(CareerService.CareerSquad squad, List<PlayerSummaryResponse> items) {
        if (items.isEmpty()) {
            return;
        }
        FeasibilityInputs.Context context = context(squad);
        Map<Long, CareerPlayerRepository.Extra> extras = careerPlayerRepository.extras(squad.careerId(), items.stream().map(PlayerSummaryResponse::getId).toList());
        for (PlayerSummaryResponse p : items) {
            p.setFeasibility(feasibilityInputs.classify(feasibilityInputs.derive(context, p.getOverall(), p.getValueEur(), p.isFreeAgent(), extras.get(p.getId()))).bucket().name());
        }
    }

    private FeasibilityInputs.Context context(CareerService.CareerSquad squad) {
        List<Integer> overalls = squad.playerIds().isEmpty() ? List.of()
                : playerService.fitPlayers(squad.playerIds(), squad.gameVersionId(), squad.careerId()).stream().map(FitPlayer::getOverall).toList();
        return feasibilityInputs.context(squad, null, overalls);
    }

    private static List<FeasibilityBucket> parseBuckets(List<String> names) {
        if (Objects.isNull(names)) {
            return List.of();
        }
        try {
            return names.stream().filter((n) -> !n.isBlank()).map((n) -> FeasibilityBucket.valueOf(n.trim().toUpperCase(java.util.Locale.ROOT))).distinct().toList();
        } catch (IllegalArgumentException e) {
            throw new InvalidTacticException("bucket");
        }
    }

    private FitRole resolveRole(String roleId) {
        FitRole role = roleService.fitRolesById().get(roleId);
        if (Objects.isNull(role)) {
            throw new NotFoundException("role", roleId);
        }
        return role;
    }

    private static String positionFor(FitRole role, String requested) {
        return Objects.nonNull(requested) && role.getPositions().contains(requested) ? requested : role.getPositions().get(0);
    }

    /** Rolün mevkisinde oynayabilen mevkiler (posDist ≥ öneri eşiği); kullanıcı mevki seçtiyse kesişim. */
    private List<String> compatible(String position, List<String> requested) {
        List<String> eligible = POSITIONS.stream().filter((p) -> posDist.factor(p, position) >= properties.getMinPositionFactor()).toList();
        if (Objects.isNull(requested) || requested.isEmpty()) {
            return eligible;
        }
        List<String> both = requested.stream().filter(eligible::contains).toList();
        return both.isEmpty() ? List.of("-") : both;
    }

    /** Önbellek anahtarı: sayfalama dışındaki tüm süzgeçler. */
    private static String signature(PlayerSearchRequest request) {
        int page = request.getPage();
        int size = request.getSize();
        List<Long> ids = request.getIds();
        request.setPage(0);
        request.setSize(0);
        request.setIds(null);
        String key = request.toString();
        request.setPage(page);
        request.setSize(size);
        request.setIds(ids);
        return key;
    }

    private static Double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
