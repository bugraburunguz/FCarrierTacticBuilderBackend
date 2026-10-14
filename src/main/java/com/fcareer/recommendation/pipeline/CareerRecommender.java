package com.fcareer.recommendation.pipeline;

import com.fcareer.billing.configuration.BillingProperties;
import com.fcareer.billing.model.response.CreditedResponse;
import com.fcareer.billing.service.CreditGuard;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Kariyer öneri hattı (13 §3.3, E3-6b). Sıra: bağlam (rol = taktikteki slotun rolü) → SQL sert kısıtlar + önskor (OVR sıralaması YOK) → FitEngineV3 ile tüm havuzda
 * hedef rol/focus için tam puan → fizibilite kovaları (kulüp gücü farkı, OVR farkı, değer/bütçe, kulüpteki rol, sözleşme) → yaş profili → MMR çeşitlendirme
 * (mevki dışı ≤ 2, prime yaş en az N) → açıklama. İçe aktarılmış kariyerde havuz yalnız kayıt dosyasındaki oyunculardır.
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
    private static final String SOURCE_TACTIC = "TACTIC";
    private static final String SOURCE_SQUAD = "SQUAD";
    private static final String SOURCE_REQUEST = "REQUEST";
    private static final String SOURCE_DEFAULT = "DEFAULT";
    private static final String SOURCE_ALTERNATIVES = "ALTERNATIVES";

    /** Tam puanlanmış aday (çeşitlendirme öncesi). */
    public record Scored(FitPlayer player, PlayerSummaryResponse summary, FitRole role, FitEngineV3.Result result, Double levelFit, String fitLabel,
                         FeasibilityBuckets.Result feasibility, double rankScore, CareerPlayerRepository.Extra extra, double futureFit, int projectedRating,
                         boolean outOfPosition, Double starterFit, Double clubStrength, Double clubGap, Double ovrGap) {
        public double currentFit() {
            return result.currentFit();
        }
    }

    /** Bir önerinin bağlamı: hedef roller + kaynak, mevcut oyuncular, kullanıcı referansları. */
    public record Ranked(List<Scored> scored, List<FitRole> targetRoles, String roleSource, Map<String, Starter> starters, Starter bestStarter, Double userClubStrength,
                         Double userFirstEleven, RecommendProperties.Profile profile, String profileName) {
    }

    public record Starter(long playerId, String name, String roleId, double fit) {
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
    private final ClubStrengthService clubStrengthService;
    private final TacticSlotLookup tacticSlotLookup;

    public CreditedResponse<CareerRecommendationResponse> recommend(Long userId, Long careerId, CareerRecommendationRequest request) {
        CareerService.CareerSquad squad = careerService.squadPlayerIds(userId, careerId);
        return creditGuard.runCharged(userId, billingProperties.getRecommendCost(), CHARGE_REASON, () -> compute(squad, request));
    }

    public CareerRecommendationResponse compute(CareerService.CareerSquad squad, CareerRecommendationRequest request) {
        Ranked ranked = rankInternal(squad, request, properties.getCandidatePool());
        int limit = Math.min(Objects.isNull(request.getLimit()) ? properties.getDefaultLimit() : request.getLimit(), properties.getMaxLimit());
        Map<Long, Scored> byId = ranked.scored().stream().collect(Collectors.toMap((s) -> s.summary().getId(), (s) -> s, (a, b) -> a));
        List<Diversifier.Candidate> candidates = ranked.scored().stream().filter((s) -> s.feasibility().bucket() != FeasibilityBucket.HAYAL).map((s) -> toCandidate(s, ranked.profile())).toList();
        List<CareerRecommendationResponse.Item> longShots = request.isIncludeLongShots()
                ? ranked.scored().stream().filter((s) -> s.feasibility().bucket() == FeasibilityBucket.HAYAL).limit(properties.getLongShots()).map((s) -> toItem(s, request.getPosition(), spoilerPolicy.ranged(squad.careerId()))).toList() : null;
        boolean hideGrowth = spoilerPolicy.ranged(squad.careerId());
        List<Diversifier.Candidate> picked = new ArrayList<>(diversifier.select(candidates, limit, properties.getMaxOutOfPosition(), ranked.profile().getMinPrime()));
        // Doğal mevkisi hedef olan oyuncular önce gelir; mevki dışı adaylar (en çok maxOutOfPosition) etiketli olarak sona eklenir.
        picked.sort(Comparator.comparing(Diversifier.Candidate::outOfPosition).thenComparing(Comparator.comparingDouble(Diversifier.Candidate::rank).reversed()));
        List<CareerRecommendationResponse.Item> items = picked.stream().map((c) -> toItem(byId.get(c.id()), request.getPosition(), hideGrowth)).toList();
        log.info("Career recommendation careerId : {} position : {} source : {} scored : {} returned : {}", squad.careerId(), request.getPosition(), ranked.roleSource(),
                ranked.scored().size(), items.size());
        FitRole single = ranked.targetRoles().size() == 1 ? ranked.targetRoles().get(0) : null;
        return CareerRecommendationResponse.builder().position(request.getPosition()).roleSource(ranked.roleSource()).profile(ranked.profileName())
                .roleId(Objects.isNull(single) ? null : single.getId()).roleName(Objects.isNull(single) ? null : single.getName()).focus(Objects.isNull(single) ? null : single.getFocus())
                .poolSize(properties.getCandidatePool()).scored(ranked.scored().size())
                .userClubStrength(round1(ranked.userClubStrength())).userFirstElevenAverage(round1(ranked.userFirstEleven()))
                .current(Objects.isNull(ranked.bestStarter()) ? null : CareerRecommendationResponse.Current.builder().playerId(ranked.bestStarter().playerId())
                        .playerName(ranked.bestStarter().name()).roleId(ranked.bestStarter().roleId()).currentFit(round1(ranked.bestStarter().fit())).build())
                .items(items).longShots(longShots).build();
    }

    /** Testler ve raporlar için: sıralı, çeşitlendirilmemiş aday listesi (poolSize recall ölçümünde değiştirilir). */
    public List<Scored> rank(CareerService.CareerSquad squad, CareerRecommendationRequest request, int poolSize) {
        return rankInternal(squad, request, poolSize).scored();
    }

    public Ranked rankInternal(CareerService.CareerSquad squad, CareerRecommendationRequest request, int poolSize) {
        String position = request.getPosition();
        String profileName = Objects.isNull(request.getProfile()) || request.getProfile().isBlank() ? properties.getDefaultProfile() : request.getProfile().toUpperCase();
        RecommendProperties.Profile profile = Optional.ofNullable(properties.getProfiles().get(profileName)).orElseThrow(() -> new NotFoundException("profile", profileName));
        List<FitRole> allRoles = rolesAtPosition(position);
        List<FitPlayer> squadPlayers = squad.playerIds().isEmpty() ? List.of() : playerService.fitPlayers(squad.playerIds(), squad.gameVersionId(), squad.careerId());
        List<FitPlayer> eligibleSquad = squadPlayers.stream().filter((p) -> canPlay(p, position)).toList();

        String[] source = new String[1];
        List<FitRole> targetRoles = resolveTargetRoles(squad.careerId(), request, allRoles, eligibleSquad, position, source);
        Map<String, Starter> starters = new HashMap<>();
        Starter bestStarter = null;
        for (FitRole role : targetRoles) {
            Starter best = null;
            for (FitPlayer p : eligibleSquad) {
                double fit = engine.currentFit(p, position, role).currentFit();
                if (Objects.isNull(best) || fit > best.fit()) {
                    best = new Starter(p.getId(), p.getName(), role.getId(), fit);
                }
            }
            if (Objects.nonNull(best)) {
                starters.put(role.getId(), best);
                if (Objects.isNull(bestStarter) || best.fit() > bestStarter.fit()) {
                    bestStarter = best;
                }
            }
        }
        ClubStrength strength = clubStrengthService.of(squad.careerId());
        String userKey = userTeamKey(squad, request);
        Double userStrength = strength.clubStrength(userKey);
        Double firstEleven = squadPlayers.stream().map(FitPlayer::getOverall).sorted(Comparator.reverseOrder()).limit(properties.getFirstEleven()).mapToInt(Integer::intValue).average()
                .stream().boxed().findFirst().orElse(null);

        Integer ageMax = Objects.isNull(request.getAgeMax()) ? profile.getMaxAge() : request.getAgeMax();
        List<PlayerSummaryResponse> pool = candidatePool(squad, request, targetRoles, poolSize, ageMax, Objects.isNull(request.getUserTeamId()));
        List<Scored> scored = new ArrayList<>();
        if (!pool.isEmpty()) {
            List<Long> ids = pool.stream().map(PlayerSummaryResponse::getId).toList();
            Map<Long, FitPlayer> players = playerService.fitPlayers(ids, squad.gameVersionId(), squad.careerId()).stream().collect(Collectors.toMap(FitPlayer::getId, (p) -> p, (a, b) -> a));
            Map<Long, CareerPlayerRepository.Extra> extras = careerPlayerRepository.extras(squad.careerId(), ids);
            LocalDate gameDate = playerSearchRepository.careerGameDate(squad.careerId()).orElse(null);
            for (PlayerSummaryResponse summary : pool) {
                FitPlayer player = players.get(summary.getId());
                if (Objects.nonNull(player)) {
                    scored.add(score(player, summary, targetRoles, position, extras.get(summary.getId()), squad, strength, userStrength, firstEleven, starters, profile, gameDate));
                }
            }
        }
        scored.sort(Comparator.comparingDouble(Scored::rankScore).reversed().thenComparingLong((s) -> s.summary().getId()));
        return new Ranked(scored, targetRoles, source[0], starters, bestStarter, userStrength, firstEleven, profile, profileName);
    }

    private Scored score(FitPlayer player, PlayerSummaryResponse summary, List<FitRole> roles, String position, CareerPlayerRepository.Extra extra, CareerService.CareerSquad squad,
                         ClubStrength strength, Double userStrength, Double firstEleven, Map<String, Starter> starters, RecommendProperties.Profile profile, LocalDate gameDate) {
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

        Double targetStrength = Objects.isNull(extra) ? null : strength.clubStrength(extra.teamKey());
        Double clubGap = Objects.nonNull(targetStrength) && Objects.nonNull(userStrength) ? targetStrength - userStrength : null;
        Double ovrGap = Objects.isNull(firstEleven) ? null : player.getOverall() - firstEleven;
        Double ratio = Objects.nonNull(squad.budgetEur()) && Objects.nonNull(summary.getValueEur()) && squad.budgetEur() > 0 ? summary.getValueEur() / (double) squad.budgetEur() : null;
        Integer months = Objects.nonNull(extra) && Objects.nonNull(extra.contractUntil()) && Objects.nonNull(gameDate)
                ? (int) Math.max(0, ChronoUnit.DAYS.between(gameDate, extra.contractUntil()) / DAYS_PER_MONTH) : null;
        FeasibilityBuckets.Result feasibility = feasibilityBuckets.classify(new FeasibilityBuckets.Input(ratio, clubGap, ovrGap, Objects.isNull(extra) ? null : extra.clubRank(), months, summary.isFreeAgent()));
        double penalty = switch (feasibility.bucket()) {
            case ALINABILIR -> 0;
            case ZOR -> properties.getRankPenalty().getHard();
            case HAYAL -> properties.getRankPenalty().getFantasy();
        };
        GrowthProjector.Projection projection = growthProjector.project(player);
        double futureFit = engine.currentFit(projection.grown(), position, bestRole).currentFit();
        double blended = (1 - profile.getFutureWeight()) * best.currentFit() + profile.getFutureWeight() * futureFit;
        double ageRisk = Objects.isNull(summary.getAge()) ? 0 : Math.max(0, summary.getAge() - profile.getAgeRiskFrom()) * profile.getAgeRiskPerYear();
        boolean oop = !player.getPositions().contains(position);
        Starter starter = starters.get(bestRole.getId());
        return new Scored(player, summary, bestRole, best, level.map(LevelFitService.LevelFit::value).orElse(null), label, feasibility, blended - penalty - ageRisk - properties.getRankPenalty().belowSquad(ovrGap), extra, futureFit,
                projection.projectedRating(), oop, Objects.isNull(starter) ? null : starter.fit(), targetStrength, clubGap, ovrGap);
    }

    /** Rol kaynağı sırası: user_tactic → mevcut kadro ataması → isteğin rolü → varsayılan; forceRole ile isteğin rolü öne geçer. alternativeRoles tüm rolleri açar. */
    private List<FitRole> resolveTargetRoles(long careerId, CareerRecommendationRequest request, List<FitRole> allRoles, List<FitPlayer> eligibleSquad, String position, String[] source) {
        if (request.isAlternativeRoles()) {
            source[0] = SOURCE_ALTERNATIVES;
            return allRoles;
        }
        boolean hasRequest = Objects.nonNull(request.getRoleId()) && !request.getRoleId().isBlank();
        if (request.isForceRole() && hasRequest) {
            List<FitRole> fromRequest = matchRoles(allRoles, request.getRoleId(), request.getFocus());
            if (!fromRequest.isEmpty()) {
                source[0] = SOURCE_REQUEST;
                return fromRequest;
            }
        }
        Optional<TacticSlotLookup.SlotRole> tactic = Objects.nonNull(request.getUserTeamId()) ? Optional.empty() : tacticSlotLookup.find(careerId, request.getSlotId(), position);
        if (tactic.isPresent()) {
            List<FitRole> fromTactic = matchRoles(allRoles, tactic.get().roleId(), tactic.get().focus());
            if (!fromTactic.isEmpty()) {
                source[0] = SOURCE_TACTIC;
                return fromTactic;
            }
        }
        FitRole assigned = currentAssignment(eligibleSquad, allRoles, position);
        if (Objects.nonNull(assigned)) {
            source[0] = SOURCE_SQUAD;
            return List.of(assigned);
        }
        if (hasRequest) {
            List<FitRole> fromRequest = matchRoles(allRoles, request.getRoleId(), request.getFocus());
            if (fromRequest.isEmpty()) {
                throw new NotFoundException("role", request.getRoleId());
            }
            source[0] = SOURCE_REQUEST;
            return fromRequest;
        }
        String base = properties.getDefaultRoles().get(position);
        List<FitRole> fromDefault = Objects.isNull(base) ? List.of() : matchRoles(allRoles, base, null);
        source[0] = SOURCE_DEFAULT;
        return fromDefault.isEmpty() ? allRoles : fromDefault;
    }

    /** Mevcut kadro ataması: mevkiyi oynayabilen oyuncular içinde en yüksek currentFit veren (oyuncu, rol/focus) çiftinin rolü. */
    private FitRole currentAssignment(List<FitPlayer> eligibleSquad, List<FitRole> allRoles, String position) {
        FitRole bestRole = null;
        double bestFit = -1;
        for (FitPlayer p : eligibleSquad) {
            for (FitRole role : allRoles) {
                double fit = engine.currentFit(p, position, role).currentFit();
                if (fit > bestFit) {
                    bestFit = fit;
                    bestRole = role;
                }
            }
        }
        return bestRole;
    }

    private static List<FitRole> matchRoles(List<FitRole> allRoles, String roleId, String focus) {
        List<FitRole> exact = allRoles.stream().filter((r) -> r.getId().equals(roleId)).toList();
        if (!exact.isEmpty()) {
            return exact;
        }
        return allRoles.stream().filter((r) -> r.getBaseRole().equals(roleId)).filter((r) -> Objects.isNull(focus) || focus.isBlank() || r.getFocus().equalsIgnoreCase(focus)).toList();
    }

    private List<FitRole> rolesAtPosition(String position) {
        List<FitRole> roles = roleService.fitRolesById().values().stream().filter((r) -> r.getPositions().contains(position)).sorted(Comparator.comparing(FitRole::getId)).toList();
        if (roles.isEmpty()) {
            throw new NotFoundException("position", position);
        }
        return roles;
    }

    private boolean canPlay(FitPlayer player, String position) {
        return player.getPositions().stream().anyMatch((p) -> p.equals(position) || posDist.factor(p, position) >= properties.getMinPositionFactor());
    }

    private String userTeamKey(CareerService.CareerSquad squad, CareerRecommendationRequest request) {
        if (Objects.nonNull(request.getUserTeamId())) {
            return "T:" + request.getUserTeamId();
        }
        return careerPlayerRepository.dominantTeamKey(squad.careerId(), squad.ownedIds()).orElse(Objects.isNull(squad.clubId()) ? null : "C:" + squad.clubId());
    }

    /**
     * Aday havuzu: her taban rol için ayrı önskor sorgusu (o rolün vektörü, `LIMIT poolSize`) birleştirilir; böylece havuz her arketipin en iyilerini taşır.
     * Sert kısıtlar SQL'de: cinsiyet, sahiplik, kadro dışı, kiralık değil, posDist ≥ eşik, yaş, edinilebilirlik, bilinen lig ulaşılabilirliği, kayıt dosyası havuzu.
     */
    private List<PlayerSummaryResponse> candidatePool(CareerService.CareerSquad squad, CareerRecommendationRequest request, List<FitRole> roles, int poolSize, Integer ageMax, boolean ownTeam) {
        Map<String, List<FitRole>> byBase = roles.stream().collect(Collectors.groupingBy(FitRole::getBaseRole, LinkedHashMap::new, Collectors.toList()));
        Map<Long, PlayerSummaryResponse> union = new LinkedHashMap<>();
        for (List<FitRole> group : byBase.values()) {
            playerSearchRepository.search(candidateSearch(squad, request, group, poolSize, ageMax, ownTeam)).forEach((s) -> union.putIfAbsent(s.getId(), s));
        }
        return new ArrayList<>(union.values());
    }

    private PlayerSearchRequest candidateSearch(CareerService.CareerSquad squad, CareerRecommendationRequest request, List<FitRole> roles, int poolSize, Integer ageMax, boolean ownTeam) {
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
                .leagueIds(ownTeam ? reachableLeagues(squad) : null)
                .ageMin(request.getAgeMin())
                .ageMax(ageMax)
                .sort("prescore")
                .prescoreWeights(prescoreWeights(position, roles))
                .page(0)
                .size(poolSize)
                .build();
    }

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

    /** Bilinen ligler için tier tablosundan sert eleme (aday lig, kullanıcınınkinden en fazla maxTierJump üstte); bilinmeyen ligler elenmez, gücü kulüp/lig gücünden türetilir. */
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

    private Diversifier.Candidate toCandidate(Scored s, RecommendProperties.Profile profile) {
        PlayerSummaryResponse p = s.summary();
        boolean young = Objects.nonNull(p.getAge()) && p.getAge() <= properties.getYoungAgeMax();
        boolean prime = Objects.nonNull(p.getAge()) && p.getAge() <= profile.getPrimeAge();
        return new Diversifier.Candidate(p.getId(), s.rankScore(), p.getClub(), p.getLeague(), ageBand(p.getAge()), s.role().getBaseRole(), p.isFreeAgent() && properties.getRankPenalty().belowSquad(s.ovrGap()) == 0, young, isReady(s), s.outOfPosition(), prime);
    }

    private boolean isReady(Scored s) {
        return Objects.nonNull(s.levelFit()) && s.levelFit() >= properties.getReadyLevelFit() && (Objects.isNull(s.starterFit()) || s.currentFit() >= s.starterFit());
    }

    private static String ageBand(Integer age) {
        if (Objects.isNull(age)) {
            return "?";
        }
        return age <= 21 ? "U21" : age <= 25 ? "22-25" : age <= 29 ? "26-29" : "30+";
    }

    private CareerRecommendationResponse.Item toItem(Scored s, String position, boolean hideGrowth) {
        Double futureFit = hideGrowth ? null : round1(s.futureFit());
        Integer projected = hideGrowth ? null : s.projectedRating();
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
        if (isReady(s)) {
            tags.add(READY);
        }
        String positionLabel = s.outOfPosition() ? "Mevki dışı (" + s.player().getPositions().get(0) + "→" + position + ")" : null;
        return CareerRecommendationResponse.Item.builder().player(s.summary()).roleId(s.role().getId()).roleName(s.role().getName()).focus(s.role().getFocus())
                .currentFit(round1(s.currentFit())).levelFit(Objects.isNull(s.levelFit()) ? null : round1(s.levelFit())).fitLabel(s.fitLabel())
                .futureFit(futureFit).projectedRating(projected)
                .deltaVsCurrent(Objects.isNull(s.starterFit()) ? null : round1(s.currentFit() - s.starterFit())).positionLabel(positionLabel)
                .clubStrength(round1(s.clubStrength())).clubStrengthGap(round1(s.clubGap())).ovrGap(round1(s.ovrGap()))
                .bucket(s.feasibility().bucket()).bucketLabel(s.feasibility().bucket().label()).bucketPoints(s.feasibility().points()).bucketFactors(s.feasibility().factors())
                .pros(pros).cons(cons).tags(tags).rankScore(round1(s.rankScore())).build();
    }

    private static FitEngineV3.Contribution rounded(FitEngineV3.Contribution c) {
        return new FitEngineV3.Contribution(c.term(), c.key(), round1(c.points()));
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static Double round1(Double v) {
        return Objects.isNull(v) ? null : Math.round(v * 10.0) / 10.0;
    }
}
