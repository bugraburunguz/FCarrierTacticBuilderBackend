package com.fcareer.fitengine.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fcareer.fitengine.model.AcceleRate;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.FitRole;
import com.fcareer.fitengine.norms.AttrNorms;
import com.fcareer.fitengine.norms.PosDist;
import com.fcareer.fitengine.norms.RoleSeeds;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * FitEngineV3 (E3-5, 13 §3.2): SAF fonksiyon, girdisi oyuncu + (mevki, rol, focus), çıktısı currentFit ∈ [0,100] ve katkı listesi.
 * currentFit kaliteyi değil rolün oyuncuya oturmasını ölçer (13 §2 madde 8); potansiyel/OVR/değer hesaba girmez.
 *   fit = posDist · clamp( 50 + Σ 100·w_i·(u_i − 0,5) − Σ k·softplus((min_e − a_e)/τ_e − 2) + PS + FAM + PHYS + FOOT )
 * Tüm katsayılar seed'lerden gelir (attr-norms, role-vectors, role-requirements, role-playstyles, role-modifiers, pos-dist); koda gömülü sayı yoktur.
 */
@Component
public class FitEngineV3 {

    public static final String VERSION = "v3";
    private static final double MAX_SCORE = 100.0;
    /** Yumuşak doygunluk dizi noktası (Buğra kararı E3-5b): 0-85 aralığı aynen, üstü tanh ile 100'e yaklaşır (kırpma yok, sıra korunur). */
    private static final double SATURATION_KNEE = 85.0;
    private static final double SOFTPLUS_SHIFT = 2.0;
    private static final double MIN_SD = 3.0;
    private static final double WEIGHT_FLOOR = 0.005;
    private static final String RIGHT = "Right";
    private static final String LEFT = "Left";

    /** term: attr | base | playstyle | familiarity | aerial | accelerate | foot | skill | position; key: ilgili attribute/PS/rol. */
    public record Contribution(String term, String key, double points) {
    }

    public record Result(double currentFit, double beforePosition, double positionFactor, boolean inPosition, List<Contribution> contributions) {

        /** En büyük n katkı (mutlak değere göre). */
        public List<Contribution> top(int n) {
            return contributions.stream().sorted((a, b) -> Double.compare(Math.abs(b.points()), Math.abs(a.points()))).limit(n).toList();
        }
    }

    private final AttrNorms norms;
    private final PosDist posDist;
    private final RoleSeeds seeds;

    @Autowired
    public FitEngineV3(AttrNorms norms, PosDist posDist, RoleSeeds seeds) {
        this.norms = norms;
        this.posDist = posDist;
        this.seeds = seeds;
    }

    public Result currentFit(FitPlayer player, String position, FitRole role) {
        return currentFit(player, position, role.getBaseRole(), role.getFocus(), role.getAcceleRates());
    }

    public Result currentFit(FitPlayer player, String position, String baseRole, String focus, Set<AcceleRate> preferredAccelerate) {
        int gender = Objects.isNull(player.getGender()) ? 0 : player.getGender();
        boolean inPosition = Objects.nonNull(player.getPositions()) && player.getPositions().contains(position);
        Map<Attr, Double> roleVector = seeds.vector(position, baseRole, focus).orElse(null);
        Map<Attr, Double> vector = Objects.nonNull(roleVector) ? roleVector : seeds.positionVector(position);
        boolean roleApplies = Objects.nonNull(roleVector);

        List<Contribution> contributions = new ArrayList<>();
        double total = seeds.scoring().path("baseline").asDouble();
        for (Map.Entry<Attr, Double> e : vector.entrySet()) {
            if (e.getValue() < WEIGHT_FLOOR) {
                continue;
            }
            double u = norms.utility(gender, position, e.getKey(), player.attr(e.getKey()));
            double points = MAX_SCORE * e.getValue() * (u - 0.5);
            contributions.add(new Contribution("attr", e.getKey().getKey(), points));
            total += points;
        }
        if (roleApplies) {
            total += baseAttrPenalties(player, position, baseRole, gender, contributions);
            total += playstyleBonus(player, baseRole, contributions);
            total += familiarity(player, baseRole, contributions);
            total += aerial(player, position, baseRole, gender, contributions);
            total += accelerate(player, preferredAccelerate, contributions);
            total += foot(player, position, baseRole, contributions);
        }
        double clamped = saturate(total);
        double factor = inPosition ? 1.0 : outOfPositionFactor(player, position);
        if (factor < 1.0) {
            contributions.add(new Contribution("position", position, clamped * (factor - 1.0)));
        }
        return new Result(clamped * factor, clamped, factor, inPosition, contributions);
    }

    /** Toplamı [0, 100) aralığına yumuşakça sıkıştırır: x <= 85 için x, üstünde 85 + 15·tanh((x-85)/15). */
    public static double saturate(double total) {
        if (total <= 0) {
            return 0;
        }
        if (total <= SATURATION_KNEE) {
            return total;
        }
        double room = MAX_SCORE - SATURATION_KNEE;
        return SATURATION_KNEE + room * Math.tanh((total - SATURATION_KNEE) / room);
    }

    private double outOfPositionFactor(FitPlayer player, String position) {
        double best = 0;
        if (Objects.nonNull(player.getPositions())) {
            for (String natural : player.getPositions()) {
                best = Math.max(best, posDist.factor(natural, position));
            }
        }
        return best;
    }

    private double baseAttrPenalties(FitPlayer player, String position, String baseRole, int gender, List<Contribution> out) {
        double k = seeds.scoring().path("baseAttrPenaltyK").asDouble();
        double sum = 0;
        for (RoleSeeds.BaseAttr base : seeds.baseAttrs(baseRole)) {
            AttrNorms.Norm n = norms.norm(gender, position, base.attr()).orElse(null);
            if (Objects.isNull(n)) {
                continue;
            }
            double sd = Math.max(n.sd(), MIN_SD);
            double min = n.p50() + base.minZ() * sd;
            double tau = Math.max(base.tauZ() * sd, 1.0);
            double penalty = k * softplus((min - player.attr(base.attr())) / tau - SOFTPLUS_SHIFT);
            out.add(new Contribution("base", base.attr().getKey(), -penalty));
            sum -= penalty;
        }
        return sum;
    }

    private double playstyleBonus(FitPlayer player, String baseRole, List<Contribution> out) {
        JsonNode cap = seeds.modifiers().path("psCap");
        double sum = 0;
        for (RoleSeeds.RolePlaystyle ps : seeds.playstyles(baseRole)) {
            Integer level = Objects.isNull(player.getPlaystyles()) ? null : player.getPlaystyles().get(ps.playstyle());
            if (Objects.nonNull(level)) {
                double points = ps.weight() * (level >= FitPlayer.PLAYSTYLE_PLUS ? cap.path("psPlusMultiplier").asInt() : 1);
                out.add(new Contribution("playstyle", ps.playstyle(), points));
                sum += points;
            }
        }
        double capped = Math.min(sum, cap.path("maxTotal").asDouble());
        return capped;
    }

    private double familiarity(FitPlayer player, String baseRole, List<Contribution> out) {
        Integer level = Objects.isNull(player.getRoleFamiliarity()) ? null : player.getRoleFamiliarity().get(baseRole);
        if (Objects.isNull(level) || level <= 0) {
            return 0;
        }
        JsonNode f = seeds.modifiers().path("familiarity");
        double points = level >= 2 ? f.path("rolePlusPlus").asDouble() : f.path("rolePlus").asDouble();
        out.add(new Contribution("familiarity", baseRole, points));
        return points;
    }

    private double aerial(FitPlayer player, String position, String baseRole, int gender, List<Contribution> out) {
        JsonNode aerial = seeds.modifiers().path("physical").path("aerial");
        if (!contains(aerial.path("roles"), baseRole)) {
            return 0;
        }
        double index = 0.4 * norms.utility(gender, position, Attr.HEADING, player.attr(Attr.HEADING))
                + 0.3 * norms.utility(gender, position, Attr.JUMPING, player.attr(Attr.JUMPING))
                + 0.3 * norms.utility(gender, position, Attr.STRENGTH, player.attr(Attr.STRENGTH));
        double height = Objects.isNull(player.getHeightCm()) ? 0 : Math.max(-1, Math.min(1, (player.getHeightCm() - aerial.path("heightCmRef").asDouble()) / 20.0));
        double signal = Math.max(-1, Math.min(1, 2 * (index - 0.5) + 0.25 * height));
        double points = aerial.path("maxPoints").asDouble() * signal;
        out.add(new Contribution("aerial", baseRole, points));
        return points;
    }

    private double accelerate(FitPlayer player, Set<AcceleRate> preferred, List<Contribution> out) {
        if (Objects.isNull(player.getAcceleRate()) || Objects.isNull(preferred) || preferred.isEmpty()) {
            return 0;
        }
        JsonNode acc = seeds.modifiers().path("physical").path("accelerate");
        double scale = acc.path("maxPoints").asDouble() * acc.path("derivedFactor").asDouble();
        double points = preferred.contains(player.getAcceleRate()) ? scale : -0.5 * scale;
        out.add(new Contribution("accelerate", player.getAcceleRate().name(), points));
        return points;
    }

    private double foot(FitPlayer player, String position, String baseRole, List<Contribution> out) {
        JsonNode foot = seeds.modifiers().path("foot");
        double sum = 0;
        String side = side(position);
        if (Objects.nonNull(side) && Objects.nonNull(player.getPreferredFoot())) {
            double scale = Objects.isNull(player.getWeakFoot()) ? 0.5 : (5 - player.getWeakFoot()) / 4.0;
            boolean opposite = !side.equals(player.getPreferredFoot().equals(LEFT) ? LEFT : RIGHT);
            if (contains(foot.path("cutInside").path("roles"), baseRole) && opposite) {
                double points = foot.path("cutInside").path("oppositeFootBonus").asDouble() * scale;
                out.add(new Contribution("foot", baseRole, points));
                sum += points;
            }
            if (contains(foot.path("crossing").path("roles"), baseRole) && !opposite) {
                double points = foot.path("crossing").path("sameFootBonus").asDouble() * scale;
                out.add(new Contribution("foot", baseRole, points));
                sum += points;
            }
        }
        if (contains(foot.path("skillMoves").path("roles"), baseRole) && Objects.nonNull(player.getSkillMoves())) {
            double points = Math.max(-1, Math.min(1, player.getSkillMoves() - 3)) * foot.path("skillMoves").path("perStar").asDouble();
            out.add(new Contribution("skill", baseRole, points));
            sum += points;
        }
        return sum;
    }

    private static String side(String position) {
        return switch (position) {
            case "LW", "LM", "LB" -> LEFT;
            case "RW", "RM", "RB" -> RIGHT;
            default -> null;
        };
    }

    private static boolean contains(JsonNode array, String value) {
        for (JsonNode n : array) {
            if (n.asText().equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static double softplus(double x) {
        return x > 30 ? x : Math.log1p(Math.exp(x));
    }
}
