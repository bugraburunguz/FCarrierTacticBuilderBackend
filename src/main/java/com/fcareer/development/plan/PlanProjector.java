package com.fcareer.development.plan;

import com.fcareer.catalog.service.PositionOverallService;
import com.fcareer.development.plan.DevelopmentPlans.Kind;
import com.fcareer.development.plan.DevelopmentPlans.Plan;
import com.fcareer.development.service.GrowthProjector;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * FIT-3 madde 9: bir gelişim planıyla N sezon sonrası oyuncu. Dengeli: bugünkü GrowthProjector (OVR = OVR + kazanç), POT tavanıyla.
 * Mevki/rol planı yalnız dağılımı değiştirir (karar): kazancın `planShare` kadarı plan attribute'larına, ama artışlar ana mevki formülündeki ağırlıklı
 * toplam Dengeli'ninkine eşit olacak biçimde ölçeklenir; OVR Dengeli'ninkiyle aynı. Rol planı Role+ ailesini `familiaritySeasonsPerLevel` sezonda bir
 * kademe artırır (en çok Role++).
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
        return project(player, plan, seasons, 0);
    }

    /**
     * GROWTH-CAL: ilk `academySeasons` sezon altyapıda (kazanç tavanı academyMaxGain), sonrası A takımda (firstTeamMaxGain). Tavan plan dağılımından
     * önce uygulanır; plan toplamı değiştirmez.
     */
    public PlanProjection project(FitPlayer player, Plan plan, int seasons, int academySeasons) {
        FitPlayer current = player;
        int overall = player.getOverall();
        // Karar: hiçbir planda (Dengeli dahil) OVR POT'u geçmez (bilinmiyorsa 99).
        int cap = Objects.isNull(player.getPotential()) ? MAX_OVERALL : Math.min(MAX_OVERALL, Math.max(player.getOverall(), player.getPotential()));
        for (int season = 1; season <= seasons; season++) {
            GrowthProjector.Stage stage = season <= academySeasons ? GrowthProjector.Stage.ACADEMY : GrowthProjector.Stage.FIRST_TEAM;
            GrowthProjector.Projection balanced = growth.project(current, stage);
            int balancedOverall = Math.min(cap, balanced.projectedRating());
            if (plan.kind() == Kind.GENERAL) {
                current = balanced.grown().toBuilder().overall(balancedOverall).build();
                overall = balancedOverall;
                continue;
            }
            // GT-a: düşüş yılında plan attribute'ları geç ve yavaş düşer; kart OVR Dengeli'ninki (yaş kuralı).
            if (growth.declining(current)) {
                overall = balancedOverall;
                current = withOverall(growth.project(current, plan.attributes(), stage).grown(), overall, familiarity(player, plan, season));
                continue;
            }
            // Karar: plan yalnız dağılımı değiştirir. Ana mevkide tahmini OVR artışı Dengeli ile aynı: artışlar, ana mevki formülündeki (doğrusal)
            // ağırlıklı toplam Dengeli'ninkine eşit olacak biçimde ölçeklenir; OVR Dengeli'nin OVR'si.
            Map<Attr, Double> planned = growth.increments(current, plan.attributes(), stage);
            if (planned.isEmpty()) {
                current = balanced.grown().toBuilder().overall(balancedOverall).roleFamiliarity(familiarity(player, plan, season)).build();
                overall = balancedOverall;
                continue;
            }
            Map<Attr, Double> weights = positionOverall.weights(mainPosition(current));
            double plannedGain = weighted(weights, planned);
            double balancedGain = weighted(weights, growth.increments(current, List.of(), stage));
            double scale = plannedGain > 0 ? balancedGain / plannedGain : 1;
            Map<Attr, Double> scaled = new EnumMap<>(Attr.class);
            planned.forEach((attr, inc) -> scaled.put(attr, inc * scale));
            overall = balancedOverall;
            current = withOverall(growth.apply(current, scaled, overall).grown(), overall, familiarity(player, plan, season));
        }
        return new PlanProjection(current, overall);
    }

    private static String mainPosition(FitPlayer player) {
        return player.getPositions().isEmpty() ? GOALKEEPER : player.getPositions().get(0);
    }

    private static double weighted(Map<Attr, Double> weights, Map<Attr, Double> increments) {
        return increments.entrySet().stream().mapToDouble((e) -> weights.getOrDefault(e.getKey(), 0.0) * e.getValue()).sum();
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
