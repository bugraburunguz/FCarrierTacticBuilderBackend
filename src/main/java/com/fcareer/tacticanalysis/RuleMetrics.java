package com.fcareer.tacticanalysis;

import com.fcareer.fitengine.model.Attr;
import com.fcareer.tacticanalysis.TacticContext.Placed;
import com.fcareer.tacticanalysis.TacticContext.Side;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * Kural koşullarının adlandırılmış metrikleri. Boş dönüş = bu taktikte uygulanamaz (örn. o tarafta kanat yok): koşul sağlanmaz, kural "değerlendirilmedi" sayılmaz.
 * Oyuncu gerektiren metrikler oyuncu yoksa da boştur (kural `requires:["players"]` ile ayrıca raporlanır).
 */
@Component
public class RuleMetrics {

    private static final Set<String> FULLBACKS = Set.of("RB", "LB", "RWB", "LWB");
    private static final Set<String> WINGERS = Set.of("LM", "RM", "LW", "RW");
    /** Forvet hattı: santrforlar + kanat forvetleri (LW/RW, 4-3-1-2 gibi dizilişlerde LF/RF). */
    private static final Set<String> STRIKERS = Set.of("ST", "CF", "LW", "RW", "LF", "RF");
    private static final Set<String> ANCHOR_POSITIONS = Set.of("CDM", "CM");
    private static final double ANCHOR_D_MIN = 0.8;
    /** DLP (her focus) Build-Up'ta stoperlerin arasına düşer: perde sayılır. */
    private static final String ANCHOR_ROLE = "deep_lying_playmaker";
    private static final long BACK_LINE_COVER = 3;
    private static final String PARAM_SIDE = "side";
    private static final String PARAM_MIN = "min";
    private static final String PARAM_ROLES = "roles";
    private static final Set<String> CENTRE_FORWARDS = Set.of("ST", "CF");
    /** Kutuya geç gelen adayı: merkez orta saha (kanatlar ve forvetler hariç). */
    private static final Set<String> MIDFIELD = Set.of("CDM", "CM", "CAM");

    private final MetricsCalculator calculator;

    public RuleMetrics(MetricsCalculator calculator) {
        this.calculator = calculator;
    }

    public OptionalDouble value(String metric, Map<String, Object> params, TacticContext ctx) {
        return switch (metric) {
            case "BAND_ORDINAL" -> OptionalDouble.of(ctx.band().ordinal());
            case "BUILDUP_IS" -> OptionalDouble.of(ctx.buildUp().name().equals(params.get("name")) ? 1 : 0);
            case "REST_DEFENCE_COUNT" -> OptionalDouble.of(calculator.restDefence(ctx).count());
            case "CENTRAL_MIDFIELD_COUNT" -> OptionalDouble.of(calculator.centralMidfieldCount(ctx));
            case "FULLBACKS_DYFWD_MIN" -> fullbacksMin(ctx);
            case "WIDTH_INDEX" -> OptionalDouble.of(calculator.calculate(ctx).width().index());
            case "CROSS_VOLUME" -> OptionalDouble.of(ctx.outfield().stream().mapToDouble((p) -> p.m().cross()).sum());
            case "AERIAL_TARGET_QUALITY" -> calculator.aerialQuality(ctx);
            case "CB_PACE_AVG" -> average(ctx, (p) -> "CB".equals(p.position()) && p.player() != null, (p) -> (p.player().attr(Attr.ACCELERATION) + p.player().attr(Attr.SPRINT_SPEED)) / 2.0);
            case "STAMINA_GAP" -> {
                Double gap = calculator.calculate(ctx).stamina().gap();
                yield gap == null ? OptionalDouble.empty() : OptionalDouble.of(gap);
            }
            case "BUILDUP_SAFETY" -> calculator.buildUpSafety(ctx);
            case "RUNBEHIND_COUNT" -> OptionalDouble.of(ctx.outfield().stream().filter((p) -> p.m().runBehind() >= number(params, PARAM_MIN)).count());
            case "FALSE9_COUNT" -> OptionalDouble.of(ctx.outfield().stream().filter((p) -> "false_9".equals(p.m().role())).count());
            case "STRIKER_RUNBEHIND_MAX" -> max(ctx, (p) -> STRIKERS.contains(p.position()), (p) -> p.m().runBehind());
            case "ANCHOR_COUNT" -> OptionalDouble.of(ctx.outfield().stream().filter((p) -> ANCHOR_POSITIONS.contains(p.position()) && (p.m().d() >= ANCHOR_D_MIN || ANCHOR_ROLE.equals(p.m().role()))).count());
            case "HIGH_LINE_THIN_REST" -> OptionalDouble.of(ctx.band().ordinal() >= number(params, "bandMin") && calculator.restDefence(ctx).count() < number(params, "restBelow") ? 1 : 0);
            case "SIDE_WINGER_CUTIN_MAX" -> max(ctx, side(params, WINGERS), (p) -> p.m().cutIn());
            case "SIDE_WINGER_W_MAX" -> max(ctx, side(params, WINGERS), (p) -> p.m().w());
            case "SIDE_FULLBACK_DYFWD_MAX" -> max(ctx, side(params, FULLBACKS), (p) -> p.m().dyFwd());
            case "SIDE_DEF_DUTY_MAX" -> max(ctx, side(params, union(FULLBACKS, WINGERS)), (p) -> p.m().d());
            // FIT-2 madde 7 (fit_engine_spec §5): kontra riski, yaratıcılık, santrfor yalnızlığı.
            // 3+ stoperli hatta bekler zaten ileri çıkar (FULLBACKS_DYFWD_MIN ile aynı gerekçe): kural dörtlü savunma içindir.
            case "OVERLAPPING_FULLBACK_COUNT" -> ctx.outfield().stream().filter((p) -> "CB".equals(p.position())).count() >= BACK_LINE_COVER ? OptionalDouble.empty()
                    : OptionalDouble.of(ctx.outfield().stream().filter((p) -> FULLBACKS.contains(p.position()) && p.m().dyFwd() >= number(params, PARAM_MIN)).count());
            case "CREATIVE_ROLE_COUNT" -> OptionalDouble.of(ctx.outfield().stream().filter(creative(params)).count());
            case "CREATIVE_VISION_AVG" -> average(ctx, creative(params).and((p) -> p.player() != null), (p) -> p.player().attr(Attr.VISION));
            case "CENTRE_FORWARD_COUNT" -> OptionalDouble.of(ctx.outfield().stream().filter((p) -> CENTRE_FORWARDS.contains(p.position())).count());
            case "WINGERS_CUTIN_MIN" -> ctx.outfield().stream().filter((p) -> WINGERS.contains(p.position())).count() < 2 ? OptionalDouble.empty()
                    : ctx.outfield().stream().filter((p) -> WINGERS.contains(p.position())).mapToDouble((p) -> p.m().cutIn()).min();
            case "LATE_RUNNER_COUNT" -> OptionalDouble.of(ctx.outfield().stream().filter((p) -> MIDFIELD.contains(p.position()) && p.m().runBehind() >= number(params, PARAM_MIN)).count());
            default -> throw new IllegalArgumentException("bilinmeyen metrik: " + metric);
        };
    }

    /** 3 ve üstü stoperli hatta bekler (wingback) zaten ileri çıkar; kural yalnız 2 stoperli dörtlü savunmada anlamlıdır. İki taraftaki beklerin (her tarafta en ileri çıkanın) ileri kayma değerlerinin küçüğü; iki tarafta da bek yoksa boş. */
    private static OptionalDouble fullbacksMin(TacticContext ctx) {
        if (ctx.outfield().stream().filter((p) -> "CB".equals(p.position())).count() >= BACK_LINE_COVER) {
            return OptionalDouble.empty();
        }
        OptionalDouble left = max(ctx, (p) -> p.side() == Side.LEFT && FULLBACKS.contains(p.position()), (p) -> p.m().dyFwd());
        OptionalDouble right = max(ctx, (p) -> p.side() == Side.RIGHT && FULLBACKS.contains(p.position()), (p) -> p.m().dyFwd());
        return left.isPresent() && right.isPresent() ? OptionalDouble.of(Math.min(left.getAsDouble(), right.getAsDouble())) : OptionalDouble.empty();
    }

    /** Yaratıcı roller seed'de (`params.roles`, virgülle ayrılmış temel rol adları). */
    private static Predicate<Placed> creative(Map<String, Object> params) {
        Set<String> roles = Set.of(String.valueOf(params.get(PARAM_ROLES)).split(","));
        return (p) -> roles.contains(p.m().role());
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        java.util.HashSet<String> all = new java.util.HashSet<>(a);
        all.addAll(b);
        return all;
    }

    private static double number(Map<String, Object> params, String key) {
        return ((Number) params.get(key)).doubleValue();
    }

    private static Predicate<Placed> side(Map<String, Object> params, Set<String> positions) {
        Side side = Side.valueOf(String.valueOf(params.get(PARAM_SIDE)));
        return (p) -> p.side() == side && positions.contains(p.position());
    }

    private static OptionalDouble max(TacticContext ctx, Predicate<Placed> filter, ToDoubleFunction<Placed> value) {
        return ctx.outfield().stream().filter(filter).mapToDouble(value).max();
    }

    private static OptionalDouble average(TacticContext ctx, Predicate<Placed> filter, ToDoubleFunction<Placed> value) {
        List<Placed> picked = ctx.placed().stream().filter(filter).toList();
        return picked.stream().mapToDouble(value).average();
    }
}
