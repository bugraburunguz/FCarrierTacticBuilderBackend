package com.fcareer.fitengine.service;

import com.fcareer.career.service.CareerService;
import com.fcareer.catalog.service.PlayerService;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.WeaponState;
import com.fcareer.fitengine.model.request.SetupCheckRequest;
import com.fcareer.fitengine.model.response.SetupCheck;
import com.fcareer.fitengine.model.response.SetupCheck.Candidate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

@Slf4j
@Service
@RequiredArgsConstructor
public class SetupCheckService {

    private static final int HIGH_LINE = 65;
    private static final int AGGRESSIVE_LINE = 85;
    private static final int LOW_BLOCK = 35;
    private static final int STARTING_CENTRE_BACKS = 2;
    private static final int PACE_HIGH_LINE = 66;
    private static final int PACE_AGGRESSIVE = 72;
    private static final int AIR_DUELS = 68;
    private static final int DEFENSIVE_READING = 65;
    private static final int BUILD_UP_PASSING = 66;
    private static final int BUILD_UP_NEEDED = 4;
    private static final int GOALKEEPER_KICKING = 65;
    private static final int COUNTER_PACE = 76;
    private static final int COUNTER_RUNNERS = 3;
    private static final Set<String> BUILD_UP_POSITIONS = Set.of("CB", "CDM", "LB", "RB");
    private static final Set<String> RUNNER_POSITIONS = Set.of("LW", "RW", "LM", "RM", "ST", "CAM");

    /** FIT-1 madde 5: kontrol başına öneri (risk sarı/kırmızıysa Lab'da gösterilir). */
    private static final Map<String, String> ADVICE = Map.of(
            "Yüksek hat: stoperlerde hız", "Savunma hattını biraz düşür ya da hızlı bir stoper ekle.",
            "Alçak blok: hava hakimiyeti", "Hava toplarında güçlü bir stoper ekle ya da hattı biraz yükselt.",
            "Orta blok: savunma okuması", "Savunma farkındalığı yüksek bir stoper dene.",
            "Kısa pasla çıkış: pas güvenliği", "Dengeli çıkışa geç ya da pası iyi bir defans/pivot oyuncusu ekle.",
            "Kısa pasla çıkış: kaleci dağıtımı", "Ayağı iyi bir kaleci dene ya da uzun topla çıkışı seç.",
            "Kontra: hızlı koşucular", "Hızlı bir kanat/forvet ekle ya da dengeli çıkışa geç.");

    private final CareerService careerService;
    private final PlayerService playerService;

    public List<SetupCheck> check(Long userId, SetupCheckRequest request) {
        CareerService.CareerSquad squad = careerService.squadPlayerIds(userId, request.getCareerId());
        List<FitPlayer> players = playerService.fitPlayers(squad.playerIds(), squad.gameVersionId(), request.getCareerId());
        List<SetupCheck> checks = new ArrayList<>();
        int depth = request.getDepth();
        if (depth >= HIGH_LINE) {
            int minPace = depth >= AGGRESSIVE_LINE ? PACE_AGGRESSIVE : PACE_HIGH_LINE;
            checks.add(build("Yüksek hat: stoperlerde hız",
                    "Yüksek/agresif hatta stoperler arkadaki boşluğu kapatabilmeli (ort. hızlanma+sprint ≥ " + minPace + ").",
                    players, Set.of("CB"), STARTING_CENTRE_BACKS, minPace, SetupCheckService::pace));
        } else if (depth <= LOW_BLOCK) {
            checks.add(build("Alçak blok: hava hakimiyeti",
                    "Geride beklerken stoperler ortaları kesmeli (ort. kafa, sıçrama, güç ≥ " + AIR_DUELS + ").",
                    players, Set.of("CB"), STARTING_CENTRE_BACKS, AIR_DUELS, SetupCheckService::air));
        } else {
            checks.add(build("Orta blok: savunma okuması",
                    "Dengeli hatta stoperler pozisyon almayı bilmeli (savunma farkındalığı ≥ " + DEFENSIVE_READING + ").",
                    players, Set.of("CB"), STARTING_CENTRE_BACKS, DEFENSIVE_READING, p -> p.attr(Attr.DEF_AWARENESS)));
        }
        String buildUp = request.getBuildUp().toLowerCase(Locale.ROOT);
        if (buildUp.startsWith("short")) {
            checks.add(build("Kısa pasla çıkış: pas güvenliği",
                    "Defans ve pivotta topu kaybetmeden oynayabilen en az " + BUILD_UP_NEEDED + " oyuncu gerekir (ort. kısa pas+top kontrolü ≥ " + BUILD_UP_PASSING + ").",
                    players, BUILD_UP_POSITIONS, BUILD_UP_NEEDED, BUILD_UP_PASSING, SetupCheckService::passing));
            checks.add(build("Kısa pasla çıkış: kaleci dağıtımı",
                    "Kaleci de kurulumda yer alır (kaleci vuruşu ≥ " + GOALKEEPER_KICKING + ").",
                    players, Set.of("GK"), 1, GOALKEEPER_KICKING, p -> p.attr(Attr.GK_KICKING)));
        } else if (buildUp.startsWith("counter")) {
            checks.add(build("Kontra: hızlı koşucular",
                    "Boşluğa koşacak en az " + COUNTER_RUNNERS + " hızlı oyuncu gerekir (ort. hızlanma+sprint ≥ " + COUNTER_PACE + ").",
                    players, RUNNER_POSITIONS, COUNTER_RUNNERS, COUNTER_PACE, SetupCheckService::pace));
        }
        log.info("Setup check careerId : {} buildUp : {} depth : {} checks : {}", request.getCareerId(), buildUp, depth, checks.size());
        return checks;
    }

    private SetupCheck build(String title, String detail, List<FitPlayer> players, Set<String> positions, int needed, int threshold,
                             ToIntFunction<FitPlayer> value) {
        List<Candidate> candidates = players.stream()
                .filter(p -> positions.contains(p.getPositions().get(0)))
                .map(p -> Candidate.builder().id(p.getId()).name(p.getName()).position(p.getPositions().get(0))
                        .value(value.applyAsInt(p)).ok(value.applyAsInt(p) >= threshold).build())
                .sorted(Comparator.comparingInt(Candidate::getValue).reversed())
                .toList();
        long ok = candidates.stream().filter(Candidate::isOk).count();
        WeaponState status = ok >= needed ? WeaponState.GREEN : ok >= needed - 1 ? WeaponState.YELLOW : WeaponState.RED;
        return SetupCheck.builder().title(title).status(status).advice(ADVICE.get(title))
                .detail(detail + " Uygun: " + ok + " / gereken " + needed + ".").players(candidates).build();
    }

    private static int pace(FitPlayer p) {
        return (p.attr(Attr.ACCELERATION) + p.attr(Attr.SPRINT_SPEED)) / 2;
    }

    private static int air(FitPlayer p) {
        return (p.attr(Attr.HEADING) + p.attr(Attr.JUMPING) + p.attr(Attr.STRENGTH)) / 3;
    }

    private static int passing(FitPlayer p) {
        return (p.attr(Attr.SHORT_PASSING) + p.attr(Attr.BALL_CONTROL)) / 2;
    }
}
