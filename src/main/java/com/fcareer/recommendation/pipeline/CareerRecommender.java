package com.fcareer.recommendation.pipeline;

import com.fcareer.billing.model.response.CreditedResponse;
import com.fcareer.billing.service.CreditGuard;
import com.fcareer.billing.configuration.BillingProperties;
import com.fcareer.career.desk.SpoilerPolicy;
import com.fcareer.career.service.CareerService;
import com.fcareer.catalog.model.request.PlayerSearchRequest;
import com.fcareer.catalog.model.response.PlayerSummaryResponse;
import com.fcareer.catalog.repository.CareerPlayerRepository;
import com.fcareer.catalog.repository.ClubRepository;
import com.fcareer.catalog.repository.LeagueRepository;
import com.fcareer.catalog.repository.PlayerSearchRepository;
import com.fcareer.catalog.service.PlayerService;
import com.fcareer.common.exception.NotFoundException;
import com.fcareer.development.service.GrowthProjector;
import com.fcareer.fitengine.configuration.FitEngineProperties;
import com.fcareer.fitengine.level.LevelFitService;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.FitRole;
import com.fcareer.fitengine.norms.PosDist;
import com.fcareer.fitengine.norms.RoleSeeds;
import com.fcareer.fitengine.service.FitEngineV3;
import com.fcareer.fitengine.service.RoleFitV3Service;
import com.fcareer.recommendation.pipeline.model.CareerRecommendationRequest;
import com.fcareer.recommendation.pipeline.model.CareerRecommendationResponse;
import com.fcareer.roles.service.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Kariyer öneri hattı (13 §3.3). Sıra: bağlam → SQL sert kısıtlar + önskor (Σ w_i·attr_i, OVR sıralaması YOK) → FitEngineV3 ile tüm havuzda tam puan →
 * fizibilite kovaları (sayısal gerekçeli) → MMR çeşitlendirme → açıklama (en büyük 3 artı + 2 eksi, Δ, futureFit). İçe aktarılmış kariyerde havuz yalnız
 * kayıt dosyasındaki oyunculardır.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CareerRecommender {

    private static final String CHARGE_REASON = "RECOMMEND_CAREER";
    private static final int PROS = 3;
    private static final int CONS = 2;
    private static final int DAYS_PER_MONTH = 30;
    private static final String FREE = "FREE";
    private static final String YOUNG = "YOUNG";
    private static final String READY = "READY";
    private static final String FREE_AGENTS_LEAGUE = "Free Agents";

    /** Tam puanlanmış aday (çeşitlendirme öncesi). */
    public record Scored(FitPlayer player, PlayerSummaryResponse summary, FitRole role, FitEngineV3.Result result, Double levelFit, String fitLabel,
                         FeasibilityBuckets.Result feasibility, double rankScore, CareerPlayerRepository.Extra extra) {
        public double currentFit() {
            return result.currentFit();
        }
    }

    private final CareerService careerService;
    private final PlayerSearchRepository playerSearchRepository;
    private final PlayerService playerService;
    private final CareerPlayerRepository careerPlayerRepository;
    private final RoleService roleService;
    private final RoleSeeds roleSeeds;
    private final PosDist posDist;
    private final FitEngineV3 engine;
    private final LevelFitService levelFitService;
    private final FitEngineProperties fitProperties;
    private final FeasibilityBuckets feasibilityBuckets;
    private final Diversifier diversifier;
    private final GrowthProjector growthProjector;
    private final ClubRepository clubRepository;
    private final LeagueRepository leagueRepository;
    private final SpoilerPolicy spoilerPolicy;
    private final RecommendProperties properties;
    private final CreditGuard creditGuard;
    private final BillingProperties billingProperties;

    public CreditedResponse<CareerRecommendationResponse> recommend(Long userId, Long careerId, CareerRecommendationRequest request) {
        CareerService.CareerSquad squad = careerService.squadPlayerIds(userId, careerId);
        return creditGuard.runCharged(userId, billingProperties.getRecommendCost(), CHARGE_REASON, () -> compute(squad, request));
    }

    public CareerRecommendationResponse compute(CareerService.CareerSquad squad, CareerRecommendationRequest request) {
        List<Scored> ranked = rank(squad, request, properties.getCandidatePool());
        List<FitRole> roles = rolesAt(request);
        Current current = currentStarter(squad, request.getPosition(), roles);
        int limit = Math.min(Objects.isNull(request.getLimit()) ? properties.getDefaultLimit() : request.getLimit(), properties.getMaxLimit());

        Map<Long, Scored> byId = ranked.stream().collect(Collectors.toMap((s) -> s.summary().getId(), (s) -> s, (a, b) -> a));
        List<Diversifier.Candidate> candidates = ranked.stream().map((s) -> toCandidate(s, current)).toList();
        boolean hideGrowth = spoilerPolicy.ranged(squad.careerId());
        List<CareerRecommendationResponse.Item> items = diversifier.select(candidates, limit).stream()
                .map((c) -> toItem(byId.get(c.id()), request.getPosition(), current, hideGrowth)).toList();
        log.info("Career recommendation careerId : {} position : {} scored : {} returned : {}", squad.careerId(), request.getPosition(), ranked.size(), items.size());
        return CareerRecommendationResponse.builder().position(request.getPosition()).poolSize(properties.getCandidatePool()).scored(ranked.size())
                .current(Objects.isNull(current) ? null : CareerRecommendationResponse.Current.builder().playerId(current.playerId()).playerName(current.name())
                        .roleId(current.roleId()).currentFit(round1(current.fit())).build())
                .items(items).build();
    }

    /** Aday üretimi + tam puanlama + fizibilite; sıralı (rankScore azalan) ve çeşitlendirilmemiş. Testler recall@10 için `poolSize`'ı değiştirir. */
    public List<Scored> rank(CareerService.CareerSquad squad, CareerRecommendationRequest request, int poolSize) {
        List<FitRole> roles = rolesAt(request);
        String position = request.getPosition();
        PlayerSearchRequest search = candidateSearch(squad, request, roles, poolSize);
        List<PlayerSummaryResponse> pool = playerSearchRepository.search(search);
        if (pool.isEmpty()) {
            return List.of();
        }
        List<Long> ids = pool.stream().map(PlayerSummaryResponse::getId).toList();
        Map<Long, FitPlayer> players = playerService.fitPlayers(ids, squad.gameVersionId(), squad.careerId()).stream().collect(Collectors.toMap(FitPlayer::getId, (p) -> p, (a, b) -> a));
        Map<Long, CareerPlayerRepository.Extra> extras = careerPlayerRepository.extras(squad.careerId(), ids);
        Integer userTier = userTier(squad);
        LocalDate gameDate = playerSearchRepository.careerGameDate(squad.careerId()).orElse(null);
        List<Scored> scored = new ArrayList<>();
        for (PlayerSummaryResponse summary : pool) {
            FitPlayer player = players.get(summary.getId());
            if (Objects.isNull(player)) {
                continue;
            }
            scored.add(score(player, summary, roles, position, extras.get(summary.getId()), squad, userTier, gameDate));
        }
        scored.sort(Comparator.comparingDouble(Scored::rankScore).reversed().thenComparingLong((s) -> s.summary().getId()));
        return scored;
    }

    private Scored score(FitPlayer player, PlayerSummaryResponse summary, List<FitRole> roles, String position, CareerPlayerRepository.Extra extra,
                         CareerService.CareerSquad squad, Integer userTier, LocalDate gameDate) {
        FitRole bestRole = null;
        FitEngineV3.Result best = null;
        for (FitRole role : roles) {
            FitEngineV3.Result result = engine.currentFit(player, position, role);
            if (Objects.isNull(best) || result.currentFit() > best.currentFit()) {
                best = result;
                bestRole = role;
            }
        }
        int gender = Objects.isNull(player.getGender()) ? 0 : player.getGender();
        Optional<LevelFitService.LevelFit> level = levelFitService.levelFit(gender, position, bestRole.getBaseRole(), bestRole.getFocus(), player.getOverall(), best.currentFit());
        String label = level.map((l) -> RoleFitV3Service.label(l.value(), fitProperties.getV3()).name()).orElse(null);

        Double ratio = Objects.nonNull(squad.budgetEur()) && Objects.nonNull(summary.getValueEur()) && squad.budgetEur() > 0 ? summary.getValueEur() / (double) squad.budgetEur() : null;
        Integer tierGap = Objects.nonNull(userTier) && Objects.nonNull(extra) && Objects.nonNull(extra.leagueTier()) ? extra.leagueTier() - userTier : null;
        Integer months = Objects.nonNull(extra) && Objects.nonNull(extra.contractUntil()) && Objects.nonNull(gameDate)
                ? (int) Math.max(0, ChronoUnit.DAYS.between(gameDate, extra.contractUntil()) / DAYS_PER_MONTH) : null;
        FeasibilityBuckets.Result feasibility = feasibilityBuckets.classify(new FeasibilityBuckets.Input(ratio, tierGap, Objects.isNull(extra) ? null : extra.clubRank(), months, summary.isFreeAgent()));
        double penalty = switch (feasibility.bucket()) {
            case ALINABILIR -> 0;
            case ZOR -> properties.getRankPenalty().getHard();
            case HAYAL -> properties.getRankPenalty().getFantasy();
        };
        return new Scored(player, summary, bestRole, best, level.map(LevelFitService.LevelFit::value).orElse(null), label, feasibility, best.currentFit() - penalty, extra);
    }

    /** SQL sert kısıtlar + önskor. OVR'ye göre sıralama/kesme yoktur. */
    private PlayerSearchRequest candidateSearch(CareerService.CareerSquad squad, CareerRecommendationRequest request, List<FitRole> roles, int poolSize) {
        String position = request.getPosition();
        List<String> eligible = allPositions().stream().filter((p) -> p.equals(position) || posDist.factor(p, position) >= properties.getMinPositionFactor()).sorted().toList();
        Long cap = Objects.isNull(squad.budgetEur()) ? null : Math.round(squad.budgetEur() * properties.getValueAlpha());
        return PlayerSearchRequest.builder()
                .gameVersionId(squad.gameVersionId())
                .gender(squad.gender())
                .careerId(squad.careerId())
                .positions(eligible)
                .excludeIds(squad.ownedIds().isEmpty() ? null : squad.ownedIds())
                .livePoolOnly(true)
                .availableOnly(true)
                .acquirableValueCap(cap)
                .includeUnvalued(true)
                .contractMonthsMax(properties.getContractMonthsMax())
                .leagueIds(reachableLeagues(squad))
                .ageMin(request.getAgeMin())
                .ageMax(request.getAgeMax())
                .sort("prescore")
                .prescoreWeights(prescoreWeights(position, roles))
                .page(0)
                .size(poolSize)
                .build();
    }

    /** Pozisyondaki (ya da seçilen) rollerin vektör ortalaması: aday havuzu herhangi bir role uyan oyuncuları kaçırmasın. */
    private Map<Attr, Double> prescoreWeights(String position, List<FitRole> roles) {
        Map<Attr, Double> sum = new EnumMap<>(Attr.class);
        int n = 0;
        for (FitRole role : roles) {
            Optional<Map<Attr, Double>> vector = roleSeeds.vector(position, role.getBaseRole(), role.getFocus());
            if (vector.isPresent()) {
                vector.get().forEach((a, w) -> sum.merge(a, w, Double::sum));
                n++;
            }
        }
        if (n == 0) {
            sum.putAll(roleSeeds.positionVector(position));
            return sum;
        }
        int count = n;
        sum.replaceAll((a, w) -> w / count);
        return sum;
    }

    /** Lig ulaşılabilirliği tier tablosundan: aday ligin seviyesi kullanıcınınkinden en fazla maxTierJump kadar üstte; seviyesi bilinmeyen ligler alt sayılır. */
    private List<Integer> reachableLeagues(CareerService.CareerSquad squad) {
        Integer userTier = userTier(squad);
        if (Objects.isNull(userTier)) {
            return null;
        }
        return leagueRepository.findAll().stream()
                .filter((l) -> Objects.isNull(l.getTier()) || l.getTier() >= userTier - properties.getMaxTierJump() || FREE_AGENTS_LEAGUE.equals(l.getName()))
                .map((l) -> l.getId()).toList();
    }

    private Integer userTier(CareerService.CareerSquad squad) {
        if (Objects.isNull(squad.clubId())) {
            return null;
        }
        return clubRepository.findById(squad.clubId()).map((c) -> c.getLeagueId()).flatMap((leagueId) -> Objects.isNull(leagueId) ? Optional.empty() : leagueRepository.findById(leagueId))
                .map((l) -> Objects.isNull(l.getTier()) ? null : l.getTier().intValue()).orElse(null);
    }

    private Set<String> allPositions() {
        Set<String> positions = new TreeSet<>();
        roleService.fitRolesById().values().forEach((r) -> positions.addAll(r.getPositions()));
        return positions;
    }

    private List<FitRole> rolesAt(CareerRecommendationRequest request) {
        String position = request.getPosition();
        List<FitRole> roles = roleService.fitRolesById().values().stream()
                .filter((r) -> r.getPositions().contains(position))
                .filter((r) -> Objects.isNull(request.getRoleId()) || request.getRoleId().isBlank() || request.getRoleId().equals(r.getId()) || request.getRoleId().equals(r.getBaseRole()))
                .sorted(Comparator.comparing(FitRole::getId)).toList();
        if (roles.isEmpty()) {
            throw new NotFoundException("role", Objects.isNull(request.getRoleId()) ? position : request.getRoleId());
        }
        return roles;
    }

    private record Current(long playerId, String name, String roleId, double fit) {
    }

    /** Mevkideki mevcut oyuncu: kadroda bu mevkiyi oynayabilen (posDist ≥ eşik) en uyumlu oyuncu. */
    private Current currentStarter(CareerService.CareerSquad squad, String position, List<FitRole> roles) {
        if (squad.playerIds().isEmpty()) {
            return null;
        }
        Current best = null;
        for (FitPlayer player : playerService.fitPlayers(squad.playerIds(), squad.gameVersionId(), squad.careerId())) {
            boolean fits = player.getPositions().stream().anyMatch((p) -> p.equals(position) || posDist.factor(p, position) >= properties.getMinPositionFactor());
            if (!fits) {
                continue;
            }
            for (FitRole role : roles) {
                double fit = engine.currentFit(player, position, role).currentFit();
                if (Objects.isNull(best) || fit > best.fit()) {
                    best = new Current(player.getId(), player.getName(), role.getId(), fit);
                }
            }
        }
        return best;
    }

    private Diversifier.Candidate toCandidate(Scored s, Current current) {
        PlayerSummaryResponse p = s.summary();
        boolean ready = Objects.nonNull(s.levelFit()) && s.levelFit() >= properties.getReadyLevelFit() && (Objects.isNull(current) || s.currentFit() >= current.fit());
        boolean young = Objects.nonNull(p.getAge()) && p.getAge() <= properties.getYoungAgeMax();
        return new Diversifier.Candidate(p.getId(), s.rankScore(), p.getClub(), p.getLeague(), ageBand(p.getAge()), s.role().getBaseRole(), p.isFreeAgent(), young, ready);
    }

    private static String ageBand(Integer age) {
        if (Objects.isNull(age)) {
            return "?";
        }
        return age <= 21 ? "U21" : age <= 25 ? "22-25" : age <= 29 ? "26-29" : "30+";
    }

    private CareerRecommendationResponse.Item toItem(Scored s, String position, Current current, boolean hideGrowth) {
        Double futureFit = null;
        Integer projected = null;
        if (!hideGrowth) {
            GrowthProjector.Projection projection = growthProjector.project(s.player());
            projected = projection.projectedRating();
            futureFit = round1(engine.currentFit(projection.grown(), position, s.role()).currentFit());
        }
        List<FitEngineV3.Contribution> pros = s.result().contributions().stream().filter((c) -> c.points() > 0)
                .sorted(Comparator.comparingDouble(FitEngineV3.Contribution::points).reversed()).limit(PROS).map(CareerRecommender::rounded).toList();
        List<FitEngineV3.Contribution> cons = s.result().contributions().stream().filter((c) -> c.points() < 0)
                .sorted(Comparator.comparingDouble(FitEngineV3.Contribution::points)).limit(CONS).map(CareerRecommender::rounded).toList();
        List<String> tags = new ArrayList<>();
        if (s.summary().isFreeAgent()) {
            tags.add(FREE);
        }
        if (Objects.nonNull(s.summary().getAge()) && s.summary().getAge() <= properties.getYoungAgeMax()) {
            tags.add(YOUNG);
        }
        if (Objects.nonNull(s.levelFit()) && s.levelFit() >= properties.getReadyLevelFit() && (Objects.isNull(current) || s.currentFit() >= current.fit())) {
            tags.add(READY);
        }
        return CareerRecommendationResponse.Item.builder().player(s.summary()).roleId(s.role().getId()).roleName(s.role().getName()).focus(s.role().getFocus())
                .currentFit(round1(s.currentFit())).levelFit(Objects.isNull(s.levelFit()) ? null : round1(s.levelFit())).fitLabel(s.fitLabel())
                .futureFit(futureFit).projectedRating(projected)
                .deltaVsCurrent(Objects.isNull(current) ? null : round1(s.currentFit() - current.fit()))
                .bucket(s.feasibility().bucket()).bucketLabel(s.feasibility().bucket().label()).bucketPoints(s.feasibility().points()).bucketFactors(s.feasibility().factors())
                .pros(pros).cons(cons).tags(tags).rankScore(round1(s.rankScore())).build();
    }

    private static FitEngineV3.Contribution rounded(FitEngineV3.Contribution c) {
        return new FitEngineV3.Contribution(c.term(), c.key(), round1(c.points()));
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
