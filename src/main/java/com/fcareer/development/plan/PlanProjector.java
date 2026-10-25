package com.fcareer.development.plan;

import com.fcareer.catalog.service.PositionOverallService;
import com.fcareer.development.plan.DevelopmentPlans.Kind;
import com.fcareer.development.plan.DevelopmentPlans.Plan;
import com.fcareer.development.service.GrowthProjector;
import com.fcareer.fitengine.model.FitPlayer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * FIT-3 madde 9: bir gelişim planıyla N sezon sonrası oyuncu. Dengeli: bugünkü GrowthProjector aynen (OVR = OVR + kazanç).
 * Mevki/rol planı: kazancın büyük kısmı plan attribute'larına (GrowthProjector.project(player, plan)); OVR etkisi ana mevkide
 * position-overall-weights formül farkıyla. Rol planı Role+ ailesini `familiaritySeasonsPerLevel` sezonda bir kademe artırır (en çok Role++).
 */
@Component
@RequiredArgsConstructor
public class PlanProjector {

    private static final int MAX_FAMILIARITY = 2;
    private static final int MAX_OVERALL = 99;
    private static final String GOALKEEPER = "GK";

    private final GrowthProjector growth;
    private final PositionOverallService positionOverall;

    public record PlanProjection(FitPlayer grown, int overall) {
    }

    public PlanProjection project(FitPlayer player, Plan plan, int seasons) {
        FitPlayer current = player;
        int overall = player.getOverall();
        for (int season = 1; season <= seasons; season++) {
            if (plan.kind() == Kind.GENERAL) {
                GrowthProjector.Projection step = growth.project(current);
                current = step.grown();
                overall = step.projectedRating();
                continue;
            }
            GrowthProjector.Projection step = growth.project(current, plan.attributes());
            overall = Math.min(MAX_OVERALL, Math.max(1, overall + formulaDelta(current, step.grown())));
            current = withOverall(step.grown(), overall, familiarity(player, plan, season));
        }
        return new PlanProjection(current, overall);
    }

    /**
     * Ana mevkide ham formül farkı. byPosition ana mevkiyi gerçek OVR'ye eşitler (artık terim); farkı görmek için ana mevki verilmez
     * (artık 0) ve iki ham değer çıkarılır.
     */
    private int formulaDelta(FitPlayer before, FitPlayer after) {
        String main = before.getPositions().isEmpty() ? null : before.getPositions().get(0);
        if (Objects.isNull(main) || Objects.isNull(before.getOverall())) {
            return 0;
        }
        boolean goalkeeper = GOALKEEPER.equals(main);
        Integer was = positionOverall.byPosition(before.getAttrs(), goalkeeper, null, before.getOverall()).get(main);
        Integer now = positionOverall.byPosition(after.getAttrs(), goalkeeper, null, before.getOverall()).get(main);
        return Objects.isNull(was) || Objects.isNull(now) ? 0 : now - was;
    }

    private Map<String, Integer> familiarity(FitPlayer player, Plan plan, int season) {
        Map<String, Integer> base = Objects.isNull(player.getRoleFamiliarity()) ? Map.of() : player.getRoleFamiliarity();
        if (!plan.roleFamiliarity() || Objects.isNull(plan.baseRole())) {
            return base;
        }
        int perLevel = Math.max(1, growth.config().getFamiliaritySeasonsPerLevel());
        Map<String, Integer> next = new HashMap<>(base);
        next.put(plan.baseRole(), Math.min(MAX_FAMILIARITY, base.getOrDefault(plan.baseRole(), 0) + season / perLevel));
        return next;
    }

    private static FitPlayer withOverall(FitPlayer p, int overall, Map<String, Integer> familiarity) {
        return p.toBuilder().overall(overall).roleFamiliarity(familiarity).positions(List.copyOf(p.getPositions())).build();
    }
}
