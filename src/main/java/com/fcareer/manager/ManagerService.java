package com.fcareer.manager;

import com.fcareer.catalog.service.PlayerService;
import com.fcareer.common.exception.NotFoundException;
import com.fcareer.manager.ManagerModels.Card;
import com.fcareer.manager.ManagerModels.ManagerProfile;
import com.fcareer.manager.ManagerModels.MissingCategory;
import com.fcareer.manager.ManagerModels.ProfileView;
import com.fcareer.manager.ManagerModels.Recommendations;
import com.fcareer.manager.ManagerModels.RecommendationRequest;
import com.fcareer.manager.ManagerRepository.ClubStat;
import com.fcareer.manager.ManagerRules.Candidate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * MGR-a: menajer profili ve kulüp önerisi. Aday havuzu itibar aralığı ve filtrelerle süzülür, kaba sıralamada (ihtiyacın itibara yakınlığı) en iyi
 * poolSize kulüp için Takım gücü hesaplanır (ClubFitCalculator, önbellekli), 6 kategori ManagerRules'tan. Hepsi tahmin (verified:false).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManagerService {

    private static final String PROFILE = "Menajer profili";
    private static final int MEN = 0;
    private static final double QUARTILE = 0.75;

    private final ManagerRepository repository;
    private final ManagerRules rules;
    private final ManagerProperties properties;
    private final ClubFitCalculator fitCalculator;
    private final PlayerService playerService;

    /** Profil yoksa null (yanıtta data null, hata değil; ana taktikteki kural). */
    @Transactional(readOnly = true)
    public ProfileView profile(Long userId) {
        return repository.profile(userId).map(this::view).orElse(null);
    }

    @Transactional
    public ProfileView save(Long userId, ManagerProfile profile) {
        validate(profile);
        repository.saveProfile(userId, profile);
        return view(profile);
    }

    private ProfileView view(ManagerProfile profile) {
        int reputation = rules.reputation(profile.background(), profile.licence());
        return new ProfileView(profile, reputation, rules.band(reputation));
    }

    /** Bean doğrulamasının ötesi: öncelikler tekrarsız, formasyon bilinen, lig var. */
    private void validate(ManagerProfile profile) {
        if (new HashSet<>(profile.priorities()).size() != profile.priorities().size()) {
            throw new IllegalArgumentException("priorities: tekrar eden öncelik");
        }
        if (Objects.nonNull(profile.formation()) && !fitCalculator.knownFormation(profile.formation())) {
            throw new IllegalArgumentException("formation: bilinmeyen formasyon " + profile.formation());
        }
        if (Objects.nonNull(profile.leagueId()) && !repository.leagueExists(profile.leagueId())) {
            throw new IllegalArgumentException("leagueId: bilinmeyen lig " + profile.leagueId());
        }
    }

    public Recommendations recommend(Long userId, RecommendationRequest request) {
        ManagerProfile profile = Objects.nonNull(request) && Objects.nonNull(request.profile()) ? request.profile()
                : repository.profile(userId).orElseThrow(() -> new NotFoundException(PROFILE, userId));
        validate(profile);
        int reputation = rules.reputation(profile.background(), profile.licence());
        Integer leagueId = Objects.nonNull(request) && Objects.nonNull(request.leagueId()) ? request.leagueId() : profile.leagueId();
        if (Objects.nonNull(leagueId) && !repository.leagueExists(leagueId)) {
            throw new IllegalArgumentException("leagueId: bilinmeyen lig " + leagueId);
        }
        int gender = Objects.nonNull(request) && Objects.nonNull(request.gender()) ? request.gender() : Objects.requireNonNullElse(profile.gender(), MEN);
        short gv = playerService.resolveGameVersion(null);
        if (repository.profileCount(gv) == 0) {
            return new Recommendations(reputation, rules.band(reputation), false, List.of(), List.of());
        }
        List<ClubStat> stats = repository.clubStats(gv, gender, properties);
        Map<Long, Integer> needs = rules.needs(stats.stream().map((s) -> new ManagerRules.Prestige(s.clubId(), s.domestic(), s.international(), s.leagueLevel())).toList());
        Map<Long, Integer> strengthPct = ManagerRules.percentiles(stats.stream().collect(Collectors.toMap(ClubStat::clubId, ClubStat::avgOvr)));
        Map<Long, Integer> squadRank = new HashMap<>();
        Map<Long, Integer> leagueSize = new HashMap<>();
        Map<Long, Boolean> budgetTop = new HashMap<>();
        Map<Long, Boolean> prestigious = new HashMap<>();
        stats.stream().collect(Collectors.groupingBy((s) -> Objects.requireNonNullElse(s.leagueId(), -1))).values().forEach((league) -> {
            List<ClubStat> byStrength = league.stream().sorted(Comparator.comparingDouble(ClubStat::avgOvr).reversed()).toList();
            List<ClubStat> byPrestige = league.stream().sorted(Comparator.comparingInt(ClubStat::domestic).reversed()).toList();
            // Karar: "üst çeyrek" kulüp değerine göre (tahmini bütçe yalnız gösterim).
            List<Long> worths = league.stream().map((s) -> Objects.requireNonNullElse(s.clubWorthEur(), 0L)).sorted().toList();
            long cut = worths.get((int) Math.floor((worths.size() - 1) * QUARTILE));
            int third = (int) Math.ceil(league.size() / 3.0);
            for (int i = 0; i < byStrength.size(); i++) {
                squadRank.put(byStrength.get(i).clubId(), i + 1);
                leagueSize.put(byStrength.get(i).clubId(), league.size());
                prestigious.put(byPrestige.get(i).clubId(), i < third);
            }
            league.forEach((s) -> budgetTop.put(s.clubId(), Objects.nonNull(s.clubWorthEur()) && s.clubWorthEur() > 0 && s.clubWorthEur() >= cut));
        });

        // Aday havuzu: itibar aralığı (ilgi + hayal) ve filtreler; kaba sıra ihtiyacın itibara yakınlığı; ilk poolSize kulüp için uyum.
        String version = repository.catalogVersion(gv);
        Map<Long, ClubStat> byId = stats.stream().collect(Collectors.toMap(ClubStat::clubId, (s) -> s));
        Map<Long, Integer> filtered = new HashMap<>();
        stats.stream().filter((s) -> Objects.isNull(leagueId) || leagueId.equals(s.leagueId())).forEach((s) -> filtered.put(s.clubId(), needs.get(s.clubId())));
        List<ClubStat> pool = rules.pool(filtered, reputation, properties.getPoolSize(), properties.getDreamPoolSize()).stream().map(byId::get).toList();
        List<Candidate> candidates = new ArrayList<>();
        Map<Long, ClubFitCalculator.ClubFit> fits = new HashMap<>();
        for (ClubStat s : pool) {
            ClubFitCalculator.ClubFit fit = fitCalculator.fit((int) s.clubId(), gv, profile.formation(), profile.style(), version);
            if (Objects.isNull(fit.squadFit())) {
                continue;
            }
            fits.put(s.clubId(), fit);
            int need = needs.get(s.clubId());
            Long budget = Math.round(s.topValueEur() * properties.getBudgetShare());
            candidates.add(new Candidate(s.clubId(), s.name(), s.leagueId(), s.league(), need, fit.squadFit(), s.youthDevelopment(), s.youngCount(),
                    squadRank.get(s.clubId()), leagueSize.get(s.clubId()), s.avgOvr(), budget > 0 ? budget : null, budgetTop.get(s.clubId()),
                    strengthPct.get(s.clubId()), rules.difficulty(squadRank.get(s.clubId()), leagueSize.get(s.clubId()), prestigious.get(s.clubId()), need, reputation),
                    s.clubWorthEur()));
        }
        ManagerRules.Selection selection = rules.select(candidates, reputation, profile.priorities(), profile.difficulty());
        List<Card> cards = selection.cards().stream().map((p) -> {
            Candidate c = p.candidate();
            ClubFitCalculator.ClubFit fit = fits.get(c.clubId());
            return new Card(p.category(), c.clubId(), c.clubName(), c.leagueId(), c.league(), c.difficulty(), rules.evidence(c),
                    rules.expectation(reputation, c.need()), fit.formation(), profile.style(), fit.squadFit(), fit.levelFit(), c.need(),
                    p.category() == ManagerModels.Category.DREAM);
        }).toList();
        List<MissingCategory> missing = selection.missing().stream().map((m) -> new MissingCategory(m.category(), m.reason())).toList();
        log.info("Manager club recommendations userId : {} reputation : {} pool : {} candidates : {} cards : {}", userId, reputation, pool.size(), candidates.size(), cards.size());
        return new Recommendations(reputation, rules.band(reputation), true, cards, missing);
    }
}
