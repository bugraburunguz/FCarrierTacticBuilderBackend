package com.fcareer.career.academy;

import com.fcareer.career.academy.AcademyRepository.Row;
import com.fcareer.career.academy.AcademyRepository.Team;
import com.fcareer.career.desk.SpoilerPolicy;
import com.fcareer.career.service.CareerService;
import com.fcareer.common.exception.NotFoundException;
import com.fcareer.development.recommend.DevelopmentRecommender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * ACADEMY-a: kariyerin altyapısı. Altyapı takımı içe aktarmada veriden bulunur (AcademyDetector); oyuncular A takım kadrosuna girmez, burada döner.
 * career_youthplayers alanlarının anlamı doğrulanmadı: youthVerified her zaman false. Aralık modunda potansiyel 5'lik banda maskelenir; potansiyelle
 * ilgili doğrulanmamış alanlar (potentialVariance, swingLowPotential) bantla birleşip tam değeri verebileceği için dönmez.
 * found=false iken teamId/name dolu olabilir: takım bulundu ama export'ta görünen oyuncusu yok (teamId null → altyapı bulunamadı).
 * ACADEMY-b: satır durumu (READY/GROWING/WAITING), Gelişim genç listesi (/youth) ve oyuncu paneli (/youth/{id}).
 */
@Service
@RequiredArgsConstructor
public class AcademyService {

    public static final String EMPTY_MESSAGE = "Altyapıda oyuncu yok ya da export'ta görünmüyor";
    private static final String PLAYER = "Oyuncu";

    private final CareerService careerService;
    private final AcademyRepository repository;
    private final SpoilerPolicy spoilerPolicy;
    private final AcademyAdvisor advisor;
    private final AcademyProperties properties;
    private final DevelopmentRecommender.Config youth;

    private static final AcademyAdvisor.Advice EMPTY_ADVICE = new AcademyAdvisor.Advice(null, null, null, null, null);

    /** status: READY (Hazır), GROWING (Gelişiyor), WAITING (Bekliyor); kadro gençlerinde null. loanedOut: başka kulübe kiralık (Gelişim). */
    public record AcademyPlayer(long playerId, String name, Integer age, List<String> positions, Integer overall, Integer potential, String potentialRange,
                                Integer monthsInSquad, Integer playerTier, Integer potentialVariance, Integer swingLowPotential, boolean youthVerified,
                                String bestPosition, String sideReason, String conversionPosition, AcademyAdvisor.PlanAdvice plan, AcademyAdvisor.Promote promote,
                                String status, boolean loanedOut) {
    }

    public record Academy(boolean found, Integer teamId, String name, String message, List<AcademyPlayer> players) {
    }

    /** Panel: oyuncunun satırı + üç bölüm. academy: altyapı oyuncusu mu (yoksa kadro genci). */
    public record YouthDetail(long playerId, String name, Integer age, List<String> positions, Integer overall, Integer potential, String potentialRange,
                              Integer monthsInSquad, boolean academy, AcademyAdvisor.WhatToDo advice, AcademyAdvisor.WhichPosition position,
                              AcademyAdvisor.TeamPlace team) {
    }

    @Transactional(readOnly = true)
    public Academy academy(Long userId, Long careerId) {
        careerService.requireOwned(userId, careerId);
        Optional<Team> team = repository.team(careerId);
        List<Row> rows = team.map((t) -> repository.players(careerId, t.teamId())).orElse(List.of());
        if (rows.isEmpty()) {
            return new Academy(false, team.map(Team::teamId).orElse(null), team.map(Team::name).orElse(null), EMPTY_MESSAGE, List.of());
        }
        return new Academy(true, team.get().teamId(), team.get().name(), null, players(userId, careerId, rows, true, Set.of()));
    }

    /** Gelişim planları: A takımda (kiralıktakiler dahil) ≤ maxAge yaş ve potansiyeli OVR'den ≥ minHeadroom yüksek oyuncular. */
    @Transactional(readOnly = true)
    public List<AcademyPlayer> youth(Long userId, Long careerId) {
        CareerService.CareerSquad squad = careerService.squadPlayerIds(userId, careerId);
        Set<Long> loanedOut = new HashSet<>(squad.ownedIds());
        loanedOut.removeAll(squad.playerIds());
        List<Row> rows = repository.playersByIds(careerId, squad.ownedIds()).stream()
                .filter((r) -> Objects.nonNull(r.age()) && Objects.nonNull(r.overall()) && Objects.nonNull(r.potential())
                        && r.age() <= youth.getMaxAge() && r.potential() - r.overall() >= youth.getMinHeadroom())
                .toList();
        return rows.isEmpty() ? List.of() : players(userId, careerId, rows, false, loanedOut);
    }

    /** Panel yalnız bu kariyerin altyapı ya da kadro oyuncusu için; başka oyuncu (ve başkasının kariyeri) 404. */
    @Transactional(readOnly = true)
    public YouthDetail detail(Long userId, Long careerId, Long playerId) {
        CareerService.CareerSquad squad = careerService.squadPlayerIds(userId, careerId);
        Optional<Team> team = repository.team(careerId);
        boolean academy = team.map((t) -> repository.players(careerId, t.teamId()).stream().anyMatch((r) -> r.playerId() == playerId)).orElse(false);
        if (!academy && !squad.ownedIds().contains(playerId)) {
            throw new NotFoundException(PLAYER, playerId);
        }
        Row row = repository.playersByIds(careerId, List.of(playerId)).stream().findFirst().orElseThrow(() -> new NotFoundException(PLAYER, playerId));
        AcademyAdvisor.Detail detail = advisor.detail(userId, careerId, playerId).orElseThrow(() -> new NotFoundException(PLAYER, playerId));
        boolean ranged = spoilerPolicy.ranged(careerId);
        return new YouthDetail(row.playerId(), row.name(), row.age(), row.positions(), row.overall(), ranged ? null : row.potential(),
                ranged && Objects.nonNull(row.potential()) ? SpoilerPolicy.range(row.potential()) : null, row.monthsInSquad(), academy,
                detail.advice(), detail.position(), detail.team());
    }

    private List<AcademyPlayer> players(Long userId, Long careerId, List<Row> rows, boolean academy, Set<Long> loanedOut) {
        boolean ranged = spoilerPolicy.ranged(careerId);
        List<Long> ids = rows.stream().map(Row::playerId).toList();
        Map<Long, AcademyAdvisor.Advice> advice = advisor.advise(userId, careerId, ids, academy);
        Map<Long, List<Integer>> recent = academy ? repository.recentOveralls(careerId, ids, Math.max(2, properties.getStallImports())) : Map.of();
        return rows.stream().map((r) -> {
            AcademyAdvisor.Advice a = advice.getOrDefault(r.playerId(), EMPTY_ADVICE);
            String status = academy ? AcademyAdvisor.status(Objects.nonNull(a.promote()), r.monthsInSquad(), recent.get(r.playerId()), properties.getWaitingMonths()) : null;
            return new AcademyPlayer(r.playerId(), r.name(), r.age(), r.positions(), r.overall(),
                    ranged ? null : r.potential(), ranged && Objects.nonNull(r.potential()) ? SpoilerPolicy.range(r.potential()) : null,
                    r.monthsInSquad(), r.playerTier(), ranged ? null : r.potentialVariance(), ranged ? null : r.swingLowPotential(), false,
                    a.bestPosition(), a.sideReason(), a.conversionPosition(), a.plan(), a.promote(), status, loanedOut.contains(r.playerId()));
        }).toList();
    }
}
