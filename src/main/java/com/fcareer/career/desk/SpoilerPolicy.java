package com.fcareer.career.desk;

import com.fcareer.catalog.model.response.PlayerSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/**
 * Spoiler kontrolü (13 İlke 4): kariyerin tercihi SCOUT_RANGE ise gerçek potansiyel API yanıtında da dönmez; yerine 5'lik aralık verilir.
 * Maskeleme sunucuda yapılır ki istemci tarafında gizleme atlanamasın.
 */
@Component
@RequiredArgsConstructor
public class SpoilerPolicy {

    public static final String SCOUT_RANGE = "SCOUT_RANGE";
    private static final int BAND = 5;

    private final NamedParameterJdbcTemplate jdbc;

    public boolean ranged(Long careerId) {
        if (Objects.isNull(careerId)) {
            return false;
        }
        return jdbc.queryForList("SELECT spoiler_mode FROM career WHERE id = :id", Map.of("id", careerId), String.class).stream().anyMatch(SCOUT_RANGE::equals);
    }

    public static int low(int potential) {
        return potential / BAND * BAND;
    }

    public static String range(int potential) {
        int low = low(potential);
        return low + "–" + (low + BAND - 1);
    }

    public void mask(Long careerId, PlayerSummaryResponse summary) {
        if (Objects.nonNull(summary) && Objects.nonNull(summary.getPotential()) && ranged(careerId)) {
            apply(summary);
        }
    }

    public void maskAll(Long careerId, Iterable<PlayerSummaryResponse> summaries) {
        if (ranged(careerId)) {
            summaries.forEach(s -> {
                if (Objects.nonNull(s) && Objects.nonNull(s.getPotential())) {
                    apply(s);
                }
            });
        }
    }

    /** Aralık modunda geçmiş/fark gibi tek sayılık potansiyel alanları bandın alt sınırına iner (BE-SEC-05). */
    public Integer bandOrSame(Long careerId, Integer potential) {
        if (Objects.nonNull(potential) && ranged(careerId)) {
            return low(potential);
        }
        return potential;
    }

    public void maskItems(Long careerId, java.util.Collection<com.fcareer.recommendation.model.response.RecommendationItem> items) {
        if (Objects.nonNull(items) && ranged(careerId)) {
            items.forEach(i -> mask(careerId, i.getPlayer()));
        }
    }

    public void maskTargets(Long careerId, java.util.Collection<com.fcareer.recommendation.model.response.PositionTargets> positions) {
        if (Objects.nonNull(positions) && ranged(careerId)) {
            positions.forEach(p -> Objects.requireNonNullElse(p.getTargets(), java.util.List.<com.fcareer.recommendation.model.response.TransferTarget>of()).forEach(t -> mask(careerId, t.getPlayer())));
        }
    }

    public void maskScouting(Long careerId, java.util.Collection<com.fcareer.recommendation.model.response.ScoutingCandidate> candidates) {
        if (Objects.nonNull(candidates) && ranged(careerId)) {
            candidates.forEach(c -> mask(careerId, c.getPlayer()));
        }
    }

    /** Potansiyel filtresi bantlara yuvarlanır: pot_min bandın altına, pot_max bandın üstüne (ikili aramayla tam değer bulunamaz). */
    public Integer minBand(Long careerId, Integer potentialMin) {
        if (Objects.nonNull(potentialMin) && ranged(careerId)) {
            return low(potentialMin);
        }
        return potentialMin;
    }

    public Integer maxBand(Long careerId, Integer potentialMax) {
        if (Objects.nonNull(potentialMax) && ranged(careerId)) {
            return low(potentialMax) + BAND - 1;
        }
        return potentialMax;
    }

    private static void apply(PlayerSummaryResponse summary) {
        summary.setPotentialRange(range(summary.getPotential()));
        summary.setPotential(null);
    }
}
