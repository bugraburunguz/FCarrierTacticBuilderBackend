package com.fcareer.fitengine.service;

import com.fcareer.career.service.CareerService;
import com.fcareer.catalog.service.PlayerService;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.WeaponState;
import com.fcareer.fitengine.model.request.SetupCheckRequest;
import com.fcareer.fitengine.model.response.SetupCheck;
import com.fcareer.fitengine.model.response.SetupCheck.Candidate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/** Kurulum kontrolleri: hat yüksekliği ve build-up'a göre kadrodaki adayları eşikle karşılaştırır. Kontroller ve eşikler seed'de (SetupCheckRules, FIT-2 madde 7). */
@Slf4j
@Service
@RequiredArgsConstructor
public class SetupCheckService {

    private final CareerService careerService;
    private final PlayerService playerService;
    private final SetupCheckRules rules;

    public List<SetupCheck> check(Long userId, SetupCheckRequest request) {
        CareerService.CareerSquad squad = careerService.squadPlayerIds(userId, request.getCareerId());
        List<FitPlayer> players = playerService.fitPlayers(squad.playerIds(), squad.gameVersionId(), request.getCareerId());
        int depth = request.getDepth();
        List<SetupCheck> checks = rules.applicable(request.getBuildUp(), depth).stream().map((c) -> build(c, players, depth)).toList();
        log.info("Setup check careerId : {} buildUp : {} depth : {} checks : {}", request.getCareerId(), request.getBuildUp(), depth, checks.size());
        return checks;
    }

    private static SetupCheck build(SetupCheckRules.Check check, List<FitPlayer> players, int depth) {
        int threshold = check.thresholdAt(depth);
        List<Candidate> candidates = players.stream()
                .filter(p -> check.positions().contains(p.getPositions().get(0)))
                .map(p -> Candidate.builder().id(p.getId()).name(p.getName()).position(p.getPositions().get(0))
                        .value(check.metric().of(p)).ok(check.metric().of(p) >= threshold).build())
                .sorted(Comparator.comparingInt(Candidate::getValue).reversed())
                .toList();
        long ok = candidates.stream().filter(Candidate::isOk).count();
        int needed = check.needed();
        WeaponState status = ok >= needed ? WeaponState.GREEN : ok >= needed - 1 ? WeaponState.YELLOW : WeaponState.RED;
        return SetupCheck.builder().id(check.id()).title(check.title()).status(status).advice(check.advice())
                .detail(check.detailAt(depth) + " Uygun: " + ok + " / gereken " + needed + ".").players(candidates).build();
    }
}
