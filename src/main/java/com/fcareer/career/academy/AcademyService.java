package com.fcareer.career.academy;

import com.fcareer.career.academy.AcademyRepository.Row;
import com.fcareer.career.academy.AcademyRepository.Team;
import com.fcareer.career.desk.SpoilerPolicy;
import com.fcareer.career.service.CareerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * ACADEMY-a: kariyerin altyapısı. Altyapı takımı içe aktarmada veriden bulunur (AcademyDetector); oyuncular A takım kadrosuna girmez, burada döner.
 * career_youthplayers alanlarının anlamı doğrulanmadı: youthVerified her zaman false. Aralık modunda potansiyel 5'lik banda maskelenir; potansiyelle
 * ilgili doğrulanmamış alanlar (potentialVariance, swingLowPotential) bantla birleşip tam değeri verebileceği için dönmez.
 * found=false iken teamId/name dolu olabilir: takım bulundu ama export'ta görünen oyuncusu yok.
 */
@Service
@RequiredArgsConstructor
public class AcademyService {

    public static final String EMPTY_MESSAGE = "Altyapıda oyuncu yok ya da export'ta görünmüyor";

    private final CareerService careerService;
    private final AcademyRepository repository;
    private final SpoilerPolicy spoilerPolicy;
    private final AcademyAdvisor advisor;

    private static final AcademyAdvisor.Advice EMPTY_ADVICE = new AcademyAdvisor.Advice(null, null, null, null);

    public record AcademyPlayer(long playerId, String name, Integer age, List<String> positions, Integer overall, Integer potential, String potentialRange,
                                Integer monthsInSquad, Integer playerTier, Integer potentialVariance, Integer swingLowPotential, boolean youthVerified,
                                String bestPosition, String sideReason, AcademyAdvisor.PlanAdvice plan, AcademyAdvisor.Promote promote) {
    }

    public record Academy(boolean found, Integer teamId, String name, String message, List<AcademyPlayer> players) {
    }

    @Transactional(readOnly = true)
    public Academy academy(Long userId, Long careerId) {
        careerService.requireOwned(userId, careerId);
        Optional<Team> team = repository.team(careerId);
        List<Row> rows = team.map((t) -> repository.players(careerId, t.teamId())).orElse(List.of());
        if (rows.isEmpty()) {
            return new Academy(false, team.map(Team::teamId).orElse(null), team.map(Team::name).orElse(null), EMPTY_MESSAGE, List.of());
        }
        boolean ranged = spoilerPolicy.ranged(careerId);
        Map<Long, AcademyAdvisor.Advice> advice = advisor.advise(userId, careerId, rows.stream().map(Row::playerId).toList());
        List<AcademyPlayer> players = rows.stream().map((r) -> {
            AcademyAdvisor.Advice a = advice.getOrDefault(r.playerId(), EMPTY_ADVICE);
            return new AcademyPlayer(r.playerId(), r.name(), r.age(), r.positions(), r.overall(),
                    ranged ? null : r.potential(), ranged && Objects.nonNull(r.potential()) ? SpoilerPolicy.range(r.potential()) : null,
                    r.monthsInSquad(), r.playerTier(), ranged ? null : r.potentialVariance(), ranged ? null : r.swingLowPotential(), false,
                    a.bestPosition(), a.sideReason(), a.plan(), a.promote());
        }).toList();
        return new Academy(true, team.get().teamId(), team.get().name(), null, players);
    }
}
