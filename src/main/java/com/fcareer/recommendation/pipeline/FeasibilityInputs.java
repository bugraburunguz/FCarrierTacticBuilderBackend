package com.fcareer.recommendation.pipeline;

import com.fcareer.career.service.CareerService;
import com.fcareer.catalog.repository.CareerPlayerRepository;
import com.fcareer.catalog.repository.PlayerSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Ulaşılabilirlik kovasının girdileri (tek yer): öneri hattı (CareerRecommender) ve kariyer oyuncu listesi (V3-06a) aynı hesabı kullanır.
 * Bütçe oranı, kulüp gücü farkı, ilk 11 ortalamasına göre OVR farkı, kayıttaki oyun tarihine göre kalan sözleşme ayı.
 */
@Component
@RequiredArgsConstructor
public class FeasibilityInputs {

    private static final int DAYS_PER_MONTH = 30;

    private final ClubStrengthService clubStrengthService;
    private final CareerPlayerRepository careerPlayerRepository;
    private final PlayerSearchRepository playerSearchRepository;
    private final RecommendProperties properties;
    private final FeasibilityBuckets feasibilityBuckets;

    /** Kariyer düzeyi değerler: bir istek boyunca sabit. */
    public record Context(Long budgetEur, ClubStrength strength, Double userStrength, Double firstEleven, LocalDate gameDate) {
    }

    /** Aday düzeyi türetilmiş değerler (puanlama ve gerekçe de bunları kullanır) + sınıflandırıcı girdisi. */
    public record Derived(Double targetStrength, Double clubGap, Double ovrGap, FeasibilityBuckets.Input input) {
    }

    /** Kullanıcının takım anahtarı: elle seçilen takım, yoksa kadronun baskın takımı, yoksa kariyer kulübü. */
    public String userTeamKey(CareerService.CareerSquad squad, Integer userTeamId) {
        if (Objects.nonNull(userTeamId)) {
            return "T:" + userTeamId;
        }
        return careerPlayerRepository.dominantTeamKey(squad.careerId(), squad.ownedIds()).orElse(Objects.isNull(squad.clubId()) ? null : "C:" + squad.clubId());
    }

    public Context context(CareerService.CareerSquad squad, Integer userTeamId, List<Integer> squadOveralls) {
        ClubStrength strength = clubStrengthService.of(squad.careerId());
        Double userStrength = strength.clubStrength(userTeamKey(squad, userTeamId));
        Double firstEleven = squadOveralls.stream().sorted(Comparator.reverseOrder()).limit(properties.getFirstEleven()).mapToInt(Integer::intValue).average()
                .stream().boxed().findFirst().orElse(null);
        LocalDate gameDate = playerSearchRepository.careerGameDate(squad.careerId()).orElse(null);
        return new Context(squad.budgetEur(), strength, userStrength, firstEleven, gameDate);
    }

    public Derived derive(Context ctx, int overall, Long valueEur, boolean freeAgent, CareerPlayerRepository.Extra extra) {
        Double targetStrength = Objects.isNull(extra) ? null : ctx.strength().clubStrength(extra.teamKey());
        Double clubGap = Objects.nonNull(targetStrength) && Objects.nonNull(ctx.userStrength()) ? targetStrength - ctx.userStrength() : null;
        Double ovrGap = Objects.isNull(ctx.firstEleven()) ? null : overall - ctx.firstEleven();
        Double ratio = Objects.nonNull(ctx.budgetEur()) && Objects.nonNull(valueEur) && ctx.budgetEur() > 0 ? valueEur / (double) ctx.budgetEur() : null;
        Integer months = Objects.nonNull(extra) && Objects.nonNull(extra.contractUntil()) && Objects.nonNull(ctx.gameDate())
                ? (int) Math.max(0, ChronoUnit.DAYS.between(ctx.gameDate(), extra.contractUntil()) / DAYS_PER_MONTH) : null;
        return new Derived(targetStrength, clubGap, ovrGap,
                new FeasibilityBuckets.Input(ratio, clubGap, ovrGap, Objects.isNull(extra) ? null : extra.clubRank(), months, freeAgent));
    }

    public FeasibilityBuckets.Result classify(Derived derived) {
        return feasibilityBuckets.classify(derived.input());
    }
}
