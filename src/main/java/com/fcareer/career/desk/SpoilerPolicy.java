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

    private static void apply(PlayerSummaryResponse summary) {
        summary.setPotentialRange(range(summary.getPotential()));
        summary.setPotential(null);
    }
}
