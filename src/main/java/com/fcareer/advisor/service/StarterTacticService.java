package com.fcareer.advisor.service;

import com.fcareer.advisor.model.response.StarterTactic;
import com.fcareer.advisor.repository.StarterTacticRepository;
import com.fcareer.advisor.service.FeasibilityService.SquadProfile;
import com.fcareer.advisor.service.FormationRanker.RankedFormation;
import com.fcareer.career.service.CareerService;
import com.fcareer.catalog.service.PlayerService;
import com.fcareer.common.exception.SquadTooSmallException;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.request.SquadFitRequest;
import com.fcareer.fitengine.model.response.SquadFitResponse;
import com.fcareer.fitengine.service.FitService;
import com.fcareer.tactics.TacticDefaults;
import com.fcareer.tactics.model.request.TacticRequest;
import com.fcareer.tactics.service.FormationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Ücretsiz otomatik başlangıç önerisi (KARIYER-UX FAZ 1, D-31). Ana taktik yokken Masa, Kadro ve Transfer bununla hesaplar.
 * Formasyon-only sıralamanın birincisi ({@link FormationRanker}, ücretli taktik önericiyle aynı) + varsayılan kurulum ({@link TacticDefaults}) +
 * o formasyonla kredi harcamayan diziliş ({@link FitService#lineup}). Preset taraması ve ücretli içgörü yok (D-01).
 * Önbellek: kariyer + son içe aktarma + son kadro hamlesi; içe aktarma ya da alım/satış anahtarı değiştirir.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StarterTacticService {

    private static final int MIN_SQUAD = 11;
    private static final int ALTERNATIVES = 2;
    private static final int CACHE_SIZE = 256;

    private final CareerService careerService;
    private final PlayerService playerService;
    private final FormationRegistry formationRegistry;
    private final FeasibilityService feasibilityService;
    private final PositionAdviceService positionAdviceService;
    private final FormationRanker formationRanker;
    private final FitService fitService;
    private final StarterTacticRepository repository;

    private final Map<String, StarterTactic> cache = new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, StarterTactic> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    public StarterTactic starter(Long userId, Long careerId) {
        CareerService.CareerSquad squad = careerService.squadPlayerIds(userId, careerId);
        if (squad.playerIds().size() < MIN_SQUAD) {
            throw new SquadTooSmallException(squad.playerIds().size());
        }
        String key = careerId + ":" + repository.squadVersion(careerId);
        synchronized (cache) {
            StarterTactic cached = cache.get(key);
            if (Objects.nonNull(cached)) {
                return cached;
            }
        }
        StarterTactic computed = compute(userId, careerId, squad);
        synchronized (cache) {
            cache.put(key, computed);
        }
        return computed;
    }

    private StarterTactic compute(Long userId, Long careerId, CareerService.CareerSquad squad) {
        List<FitPlayer> players = playerService.fitPlayers(squad.playerIds(), squad.gameVersionId(), careerId);
        SquadProfile profile = feasibilityService.profile(players, positionAdviceService.bestScores(players));
        List<RankedFormation> ranked = formationRanker.rank(players, profile, formationRegistry.findAll());
        RankedFormation top = ranked.get(0);

        TacticRequest tactic = TacticRequest.builder().formation(top.formation().id()).setup(TacticDefaults.setup()).build();
        SquadFitRequest request = new SquadFitRequest();
        request.setCareerId(careerId);
        request.setTactic(tactic);
        SquadFitResponse lineup = fitService.lineup(userId, request);

        List<StarterTactic.Slot> slots = lineup.getSlots().stream()
                .map(s -> new StarterTactic.Slot(s.getSlotId(), s.getPosition(), s.getRoleId(), s.getRoleName())).toList();
        Map<String, Long> assigned = new LinkedHashMap<>();
        lineup.getSlots().stream().filter(s -> Objects.nonNull(s.getPlayerId())).forEach(s -> assigned.put(s.getSlotId(), s.getPlayerId()));
        List<StarterTactic.Alternative> alternatives = ranked.stream().skip(1).limit(ALTERNATIVES)
                .map(r -> new StarterTactic.Alternative(r.formation().id(), r.score())).toList();
        log.info("Starter tactic computed careerId : {} formation : {}", careerId, top.formation().id());
        return new StarterTactic(top.formation().id(), TacticDefaults.setup(), slots, assigned, lineup.getSquadFit(), alternatives);
    }
}
