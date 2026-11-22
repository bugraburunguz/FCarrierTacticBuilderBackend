package com.fcareer.manager;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fcareer.advisor.service.FeasibilityService;
import com.fcareer.advisor.service.FormationRanker;
import com.fcareer.advisor.service.PositionAdviceService;
import com.fcareer.catalog.entity.BasePlayerEntity;
import com.fcareer.catalog.entity.PlayerRatingEntity;
import com.fcareer.catalog.entity.PlayerValuationEntity;
import com.fcareer.catalog.mapper.PlayerMapper;
import com.fcareer.catalog.model.enums.ValuationSource;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.repository.PlayerRatingRepository;
import com.fcareer.catalog.repository.PlayerValuationRepository;
import com.fcareer.catalog.service.PlayerService;
import com.fcareer.common.cache.ReferenceCache;
import com.fcareer.fitengine.configuration.FitEngineProperties;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.response.SquadFitResponse;
import com.fcareer.fitengine.service.SquadFitService;
import com.fcareer.manager.ManagerModels.Style;
import com.fcareer.tactics.model.request.TacticRequest;
import com.fcareer.tactics.service.FormationRegistry;
import com.fcareer.tactics.service.TacticResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * MGR-a madde 4 "Uyum": kariyer yokken bir katalog kulübünün kadrosuyla seçilen formasyon ve tarzda Takım gücü (başlangıç önerisi motoru: formasyon
 * "Fark etmez" ise kulübün en uygun formasyonu; dizilişi SquadFitService çözer). Sonuç (kulüp, formasyon, tarz, katalog sürümü) anahtarıyla önbellekte.
 */
@Component
@RequiredArgsConstructor
public class ClubFitCalculator {

    private static final int MIN_PLAYERS = 11;
    private static final Duration TTL = Duration.ofHours(12);
    private static final String AUTO = "auto";

    private final PlayerRatingRepository ratings;
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
    private final BasePlayerRepository basePlayerRepository;
    private final PlayerValuationRepository valuationRepository;
    private final PlayerMapper playerMapper;
    private final AtomicLong computations = new AtomicLong();

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
        String key = "mgr-fit:" + gameVersionId + ":" + catalogVersion + ":" + Objects.requireNonNullElse(archivedSquadVersionId, 0L) + ":" + clubId + ":"
                + Objects.requireNonNullElse(formation, AUTO) + ":" + style;
        return cache.getOrLoad(key, new TypeReference<ClubFit>() { }, TTL, () -> compute(clubId, gameVersionId, formation, style, archivedSquadVersionId));
    }

    private ClubFit compute(int clubId, short gameVersionId, String formation, Style style, Long archivedSquadVersionId) {
        computations.incrementAndGet();
        List<FitPlayer> players = Objects.isNull(archivedSquadVersionId) ? findLiveSquad(clubId, gameVersionId)
                : findArchivedSquad(clubId, gameVersionId, archivedSquadVersionId);
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

    private List<FitPlayer> findLiveSquad(int clubId, short gameVersionId) {
        List<Long> ids = ratings.findCatalogSquad(clubId, gameVersionId).stream().map(PlayerRatingEntity::getBasePlayerId).toList();
        if (ids.size() < MIN_PLAYERS) {
            return List.of();
        }
        return playerService.fitPlayers(ids, gameVersionId);
    }

    private List<FitPlayer> findArchivedSquad(int clubId, short gameVersionId, long archivedSquadVersionId) {
        List<PlayerRatingEntity> squad = managerRepository.findArchivedCatalogSquad(clubId, archivedSquadVersionId);
        if (squad.size() < MIN_PLAYERS) {
            return List.of();
        }
        List<Long> ids = squad.stream().map(PlayerRatingEntity::getBasePlayerId).toList();
        Map<Long, BasePlayerEntity> bases = basePlayerRepository.findAllById(ids).stream().collect(Collectors.toMap(BasePlayerEntity::getId, Function.identity()));
        Map<Long, PlayerValuationEntity> valuations = valuationRepository
                .findByBasePlayerIdInAndGameVersionIdAndSourceAndOwnerUserIdIsNull(ids, gameVersionId, ValuationSource.MODELED).stream()
                .collect(Collectors.toMap(PlayerValuationEntity::getBasePlayerId, Function.identity()));
        return squad.stream().map((r) -> playerMapper.toFitPlayer(bases.get(r.getBasePlayerId()), r, valuations.get(r.getBasePlayerId()), null)).toList();
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
