package com.fcareer.development.service;

import com.fcareer.catalog.model.request.PlayerSearchRequest;
import com.fcareer.catalog.model.response.PlayerSummaryResponse;
import com.fcareer.catalog.service.PlayerService;
import com.fcareer.development.model.DevelopmentModels.DevelopmentGap;
import com.fcareer.development.model.DevelopmentModels.DevelopmentRequest;
import com.fcareer.development.model.DevelopmentModels.DevelopmentResponse;
import com.fcareer.development.model.DevelopmentModels.RoleDevelopment;
import com.fcareer.development.model.DevelopmentModels.TrainingPriority;
import com.fcareer.development.model.DevelopmentModels.WonderkidQuery;
import com.fcareer.development.model.DevelopmentModels.WonderkidResponse;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.FitRole;
import com.fcareer.fitengine.model.WeaponState;
import com.fcareer.fitengine.model.response.RoleFitResponse;
import com.fcareer.fitengine.model.response.WeaponCheck;
import com.fcareer.fitengine.configuration.FitEngineProperties;
import com.fcareer.fitengine.level.LevelFitService;
import com.fcareer.fitengine.norms.AttrNorms;
import com.fcareer.fitengine.norms.RoleSeeds;
import com.fcareer.fitengine.service.FitEngineV3;
import com.fcareer.fitengine.service.RoleFitService;
import com.fcareer.fitengine.service.RoleFitV3Service;
import com.fcareer.roles.service.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DevelopmentService {

    private static final Set<Attr> HARD_TO_TRAIN = Set.of(Attr.ACCELERATION, Attr.SPRINT_SPEED, Attr.AGILITY, Attr.BALANCE, Attr.JUMPING);
    private static final double HARD_GAIN_FACTOR = 0.3;
    private static final double TRAINABLE_GAIN_FACTOR = 1.5;
    private static final int MAX_ROLES = 12;
    private static final int MAX_PRIORITIES = 8;
    private static final int POOL_PAGES = 3;
    private static final int POOL_PAGE_SIZE = 100;
    private static final int DEFAULT_LIMIT = 40;
    private static final int MAX_LIMIT = 100;
    private static final double MILLION = 1_000_000.0;
    private static final String SORT_GAP = "gap";
    private static final String SORT_ROLE = "role";
    private static final String SORT_VALUE = "value";

    private final PlayerService playerService;
    private final RoleService roleService;
    private final RoleFitService roleFitService;
    private final FitEngineV3 engine;
    private final GrowthProjector growthProjector;
    private final LevelFitService levelFitService;
    private final FitEngineProperties fitProperties;
    private final RoleSeeds roleSeeds;
    private final AttrNorms attrNorms;
    private final com.fcareer.recommendation.pipeline.CareerPlayerList careerPlayerList;

    public DevelopmentResponse plan(Long playerId, DevelopmentRequest request) {
        FitPlayer player = playerService.fitPlayers(List.of(playerId), request.getGameVersionId(), request.getCareerId()).get(0);
        int headroom = headroom(player);
        boolean v3 = isV3(request.getCareerId());
        Set<String> targets = normalizedTargets(request.getTargetRoles());
        List<RoleDevelopment> perRole = new ArrayList<>();
        for (FitRole role : roleService.fitRolesById().values()) {
            if (!targets.isEmpty() && !matchesTarget(role, targets)) {
                continue;
            }
            bestPosition(player, role, headroom, v3).ifPresent(perRole::add);
        }
        List<RoleDevelopment> ranked = perRole.stream()
                .filter(r -> !targets.isEmpty() || r.getTotalGap() > 0)
                .sorted(Comparator.comparing(RoleDevelopment::isUnlocks).reversed()
                        .thenComparingInt(RoleDevelopment::getTotalGap)
                        .thenComparing(Comparator.comparingDouble(RoleDevelopment::getProjectedScore).reversed()))
                .limit(MAX_ROLES)
                .toList();
        RoleDevelopment best = ranked.stream().filter(r -> r.isUnlocks() && r.getTotalGap() > 0).findFirst().orElse(null);
        return DevelopmentResponse.builder()
                .playerId(player.getId()).playerName(player.getName()).age(player.getAge())
                .overall(player.getOverall()).potential(player.getPotential()).headroom(headroom)
                .estimated(true).perRole(ranked).bestPath(best)
                .trainingPriorities(priorities(ranked))
                .build();
    }

    public List<WonderkidResponse> wonderkids(WonderkidQuery query) {
        FitRole role = Objects.isNull(query.getRoleId()) || query.getRoleId().isBlank() ? null : roleService.getFitRole(query.getRoleId());
        List<PlayerSummaryResponse> pool = new ArrayList<>();
        for (int page = 0; page < POOL_PAGES; page++) {
            List<PlayerSummaryResponse> items = playerService.search(searchRequest(query, page)).getItems();
            pool.addAll(items);
            if (items.size() < POOL_PAGE_SIZE) {
                break;
            }
        }
        Map<Long, FitPlayer> fitById = new HashMap<>();
        if (Objects.nonNull(role) && !pool.isEmpty()) {
            playerService.fitPlayers(pool.stream().map(PlayerSummaryResponse::getId).toList(), query.getGameVersionId(), query.getCareerId())
                    .forEach(p -> fitById.put(p.getId(), p));
        }
        List<WonderkidResponse> result = new ArrayList<>();
        for (PlayerSummaryResponse summary : pool) {
            if (Objects.isNull(summary.getPotential())) {
                continue;
            }
            WonderkidResponse row = toRow(summary);
            if (Objects.nonNull(role)) {
                RoleDevelopment dev = Objects.isNull(fitById.get(summary.getId())) ? null
                        : bestPosition(fitById.get(summary.getId()), role, headroom(fitById.get(summary.getId())), isV3(query.getCareerId())).orElse(null);
                if (Objects.isNull(dev)) {
                    continue;
                }
                row.setRoleId(role.getId());
                row.setRoleName(role.getName());
                row.setRolePosition(dev.getPosition());
                row.setRoleScore(dev.getCurrentScore());
                row.setRoleBadge(dev.getCurrentBadge());
                row.setProjectedBadge(dev.getProjectedBadge());
                row.setRoleLevelFit(dev.getCurrentLevelFit());
                row.setRoleFitLabel(dev.getCurrentFitLabel());
                row.setProjectedFitLabel(dev.getProjectedFitLabel());
            }
            result.add(row);
        }
        result.sort(comparator(query.getSort(), Objects.nonNull(role)));
        int limit = query.getLimit() <= 0 ? DEFAULT_LIMIT : Math.min(query.getLimit(), MAX_LIMIT);
        List<WonderkidResponse> rows = result.stream().limit(limit).toList();
        if (Objects.nonNull(query.getCareerId()) && Objects.nonNull(query.getUserId())) {
            Map<Long, String> buckets = careerPlayerList.feasibility(query.getUserId(), query.getCareerId(), rows.stream().map(WonderkidResponse::getId).toList());
            rows.forEach((r) -> r.setFeasibility(buckets.get(r.getId())));
        }
        return rows;
    }

    private PlayerSearchRequest searchRequest(WonderkidQuery query, int page) {
        PlayerSearchRequest request = PlayerSearchRequest.builder()
                .gameVersionId(query.getGameVersionId())
                .gender(query.getGender())
                .ageMax(query.getMaxAge())
                .potentialMin(query.getMinPotential())
                .leagueId(query.getLeagueId())
                .valueMax(query.getBudgetMax())
                .careerId(query.getCareerId())
                .sort("potential")
                .ascending(false)
                .page(page)
                .size(POOL_PAGE_SIZE)
                .build();
        if (Objects.nonNull(query.getPosition()) && !query.getPosition().isBlank()) {
            request.setPositions(List.of(query.getPosition()));
        }
        return request;
    }

    private WonderkidResponse toRow(PlayerSummaryResponse summary) {
        int potential = summary.getPotential();
        Double perMillion = Objects.nonNull(summary.getValueEur()) && summary.getValueEur() > 0
                ? Math.round(potential / (summary.getValueEur() / MILLION) * 100.0) / 100.0 : null;
        return WonderkidResponse.builder()
                .id(summary.getId()).name(summary.getName()).age(summary.getAge())
                .overall(summary.getOverall()).potential(potential).gap(potential - summary.getOverall())
                .positions(summary.getPositions()).club(summary.getClub()).league(summary.getLeague())
                .valueEur(summary.getValueEur()).potentialPerMillion(perMillion)
                .build();
    }

    private Comparator<WonderkidResponse> comparator(String sort, boolean hasRole) {
        Comparator<WonderkidResponse> byPotential = Comparator.comparingInt(WonderkidResponse::getPotential).reversed()
                .thenComparing(Comparator.comparingInt(WonderkidResponse::getOverall).reversed());
        if (SORT_GAP.equalsIgnoreCase(sort)) {
            return Comparator.comparingInt(WonderkidResponse::getGap).reversed().thenComparing(byPotential);
        }
        if (SORT_ROLE.equalsIgnoreCase(sort) && hasRole) {
            return Comparator.comparing(WonderkidResponse::getRoleScore, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(byPotential);
        }
        if (SORT_VALUE.equalsIgnoreCase(sort)) {
            return Comparator.comparing(WonderkidResponse::getPotentialPerMillion, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(byPotential);
        }
        return byPotential;
    }

    private java.util.Optional<RoleDevelopment> bestPosition(FitPlayer player, FitRole role, int headroom, boolean v3) {
        RoleDevelopment best = null;
        for (String position : player.getPositions()) {
            if (!role.getPositions().contains(position)) {
                continue;
            }
            RoleDevelopment candidate = v3 ? evaluateV3(player, role, position, headroom) : evaluate(player, role, position, headroom);
            if (Objects.isNull(best) || candidate.getTotalGap() < best.getTotalGap()
                    || (candidate.getTotalGap() == best.getTotalGap() && candidate.getProjectedScore() > best.getProjectedScore())) {
                best = candidate;
            }
        }
        return java.util.Optional.ofNullable(best);
    }

    /** Kariyerde v3 motoru (career-engine-version); katalog/UT çağrıları v1'de kalır. */
    private boolean isV3(Long careerId) {
        return Objects.nonNull(careerId) && FitEngineV3.VERSION.equals(fitProperties.getCareerEngineVersion());
    }

    /**
     * v3 gelişim: currentScore = FitEngineV3.currentFit; projectedScore = bir sezon büyüme sonrası (GrowthProjector) currentFit; projectedRating = sezon sonu beklenen OVR.
     * Açıklar (gaps) rolün temel attribute'larındaki seviye-normalize alt sınırdır (attr-norms + role-requirements).
     */
    private RoleDevelopment evaluateV3(FitPlayer player, FitRole role, String position, int headroom) {
        FitEngineV3.Result current = engine.currentFit(player, position, role);
        GrowthProjector.Projection projection = growthProjector.project(player);
        FitEngineV3.Result future = engine.currentFit(projection.grown(), position, role);
        List<DevelopmentGap> gaps = baseGaps(player, position, role, headroom);
        boolean unlocks = !gaps.isEmpty() && gaps.stream().allMatch(DevelopmentGap::isClosable);
        Optional<LevelFitService.LevelFit> level = v3Level(player, position, role, current);
        Optional<LevelFitService.LevelFit> futureLevel = v3Level(projection.grown(), position, role, future);
        Optional<RoleFitV3Service.FitLabel> label = level.map((l) -> RoleFitV3Service.label(l.value(), fitProperties.getV3()));
        Optional<RoleFitV3Service.FitLabel> futureLabel = futureLevel.map((l) -> RoleFitV3Service.label(l.value(), fitProperties.getV3()));
        return RoleDevelopment.builder()
                .roleId(role.getId()).roleName(role.getName()).position(position).focus(role.getFocus())
                .currentScore(round1(current.currentFit())).currentBadge(label.map(RoleFitV3Service.FitLabel::badge).orElse(null))
                .currentLevelFit(level.map((l) -> round1(l.value())).orElse(null)).currentFitLabel(label.map(Enum::name).orElse(null))
                .gaps(gaps).totalGap(gaps.stream().mapToInt(DevelopmentGap::getGap).sum())
                .projectedScore(round1(future.currentFit())).projectedBadge(futureLabel.map(RoleFitV3Service.FitLabel::badge).orElse(null))
                .projectedFitLabel(futureLabel.map(Enum::name).orElse(null))
                .projectedRating(projection.projectedRating()).unlocks(unlocks)
                .build();
    }

    /** Rozet, etiket ve gösterilen yüzde aynı sayıdan (levelFit; V3-07c 0a). */
    private Optional<LevelFitService.LevelFit> v3Level(FitPlayer player, String position, FitRole role, FitEngineV3.Result result) {
        int gender = Objects.isNull(player.getGender()) ? 0 : player.getGender();
        return levelFitService.levelFit(gender, position, role.getBaseRole(), role.getFocus(), player.getOverall(), result.currentFit());
    }

    private List<DevelopmentGap> baseGaps(FitPlayer player, String position, FitRole role, int headroom) {
        int gender = Objects.isNull(player.getGender()) ? 0 : player.getGender();
        List<DevelopmentGap> gaps = new ArrayList<>();
        for (RoleSeeds.BaseAttr base : roleSeeds.baseAttrs(role.getBaseRole())) {
            AttrNorms.Norm norm = attrNorms.norm(gender, position, base.attr()).orElse(null);
            if (Objects.isNull(norm)) {
                continue;
            }
            int needed = (int) Math.round(norm.p50() + base.minZ() * Math.max(norm.sd(), 3.0));
            int value = player.attr(base.attr());
            if (value >= needed) {
                continue;
            }
            boolean hard = HARD_TO_TRAIN.contains(base.attr());
            int gap = needed - value;
            int maxGain = maxGain(headroom, hard);
            gaps.add(DevelopmentGap.builder().attr(base.attr().getKey()).value(value).needed(needed).gap(gap).maxGain(maxGain).closable(gap <= maxGain).hardToTrain(hard).build());
        }
        return gaps;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private RoleDevelopment evaluate(FitPlayer player, FitRole role, String position, int headroom) {
        RoleFitResponse current = roleFitService.calculate(player, role, null, position);
        List<DevelopmentGap> gaps = gaps(current, headroom);
        FitPlayer grown = grow(player, gaps);
        RoleFitResponse projected = roleFitService.calculate(grown, role, null, position);
        boolean unlocks = !gaps.isEmpty() && gaps.stream().allMatch(DevelopmentGap::isClosable);
        return RoleDevelopment.builder()
                .roleId(role.getId()).roleName(role.getName()).position(position).focus(role.getFocus())
                .currentScore(current.getScore()).currentBadge(current.getBadge())
                .gaps(gaps).totalGap(gaps.stream().mapToInt(DevelopmentGap::getGap).sum())
                .projectedScore(projected.getScore()).projectedBadge(projected.getBadge())
                .projectedRating(projected.getProjectedRating()).unlocks(unlocks)
                .build();
    }

    private List<DevelopmentGap> gaps(RoleFitResponse fit, int headroom) {
        if (Objects.isNull(fit.getWeaponChecks())) {
            return List.of();
        }
        List<DevelopmentGap> gaps = new ArrayList<>();
        for (WeaponCheck check : fit.getWeaponChecks()) {
            if (check.getValue() >= check.getEffectiveMin()) {
                continue;
            }
            boolean hard = Attr.fromKey(check.getAttr()).map(HARD_TO_TRAIN::contains).orElse(false);
            int gap = check.getEffectiveMin() - check.getValue();
            int maxGain = maxGain(headroom, hard);
            gaps.add(DevelopmentGap.builder().attr(check.getAttr()).value(check.getValue()).needed(check.getEffectiveMin())
                    .gap(gap).maxGain(maxGain).closable(gap <= maxGain).hardToTrain(hard).build());
        }
        return gaps;
    }

    private static int maxGain(int headroom, boolean hard) {
        return (int) Math.round(headroom * (hard ? HARD_GAIN_FACTOR : TRAINABLE_GAIN_FACTOR));
    }

    private FitPlayer grow(FitPlayer player, List<DevelopmentGap> gaps) {
        Map<Attr, Integer> attrs = new EnumMap<>(Attr.class);
        attrs.putAll(player.getAttrs());
        for (DevelopmentGap gap : gaps) {
            Attr.fromKey(gap.getAttr()).ifPresent(attr -> attrs.merge(attr, Math.min(gap.getGap(), gap.getMaxGain()), Integer::sum));
        }
        return FitPlayer.builder().id(player.getId()).name(player.getName()).age(player.getAge()).overall(player.getOverall())
                .positions(player.getPositions()).attrs(attrs).playstyles(player.getPlaystyles()).acceleRate(player.getAcceleRate())
                .potential(player.getPotential()).valueEur(player.getValueEur()).build();
    }

    private List<TrainingPriority> priorities(List<RoleDevelopment> roles) {
        Map<String, int[]> totals = new HashMap<>();
        Map<String, Boolean> hard = new HashMap<>();
        roles.stream().filter(RoleDevelopment::isUnlocks).forEach(r -> r.getGaps().forEach(g -> {
            int[] agg = totals.computeIfAbsent(g.getAttr(), k -> new int[2]);
            agg[0] += g.getGap();
            agg[1]++;
            hard.put(g.getAttr(), g.isHardToTrain());
        }));
        return totals.entrySet().stream()
                .map(e -> TrainingPriority.builder().attr(e.getKey()).totalGap(e.getValue()[0]).roles(e.getValue()[1])
                        .hardToTrain(hard.get(e.getKey())).build())
                .sorted(Comparator.comparingInt(TrainingPriority::getRoles).reversed().thenComparing(Comparator.comparingInt(TrainingPriority::getTotalGap).reversed()))
                .limit(MAX_PRIORITIES)
                .toList();
    }

    private static int headroom(FitPlayer player) {
        return Objects.isNull(player.getPotential()) ? 0 : Math.max(0, player.getPotential() - player.getOverall());
    }

    private static Set<String> normalizedTargets(List<String> targets) {
        if (Objects.isNull(targets)) {
            return Set.of();
        }
        return targets.stream().filter(Objects::nonNull).map(t -> t.trim().toLowerCase()).collect(java.util.stream.Collectors.toSet());
    }

    private static boolean matchesTarget(FitRole role, Set<String> targets) {
        return targets.contains(role.getId().toLowerCase()) || targets.contains(role.getName().toLowerCase());
    }
}
