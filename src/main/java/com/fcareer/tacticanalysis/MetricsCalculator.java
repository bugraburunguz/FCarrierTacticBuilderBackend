package com.fcareer.tacticanalysis;

import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.tacticanalysis.TacticAnalysisModels.AttackDefence;
import com.fcareer.tacticanalysis.TacticAnalysisModels.BuildUp;
import com.fcareer.tacticanalysis.TacticAnalysisModels.Channel;
import com.fcareer.tacticanalysis.TacticAnalysisModels.Length;
import com.fcareer.tacticanalysis.TacticAnalysisModels.Metrics;
import com.fcareer.tacticanalysis.TacticAnalysisModels.Opportunity;
import com.fcareer.tacticanalysis.TacticAnalysisModels.RestDefence;
import com.fcareer.tacticanalysis.TacticAnalysisModels.Stamina;
import com.fcareer.tacticanalysis.TacticAnalysisModels.Width;
import com.fcareer.tacticanalysis.TacticContext.Placed;
import com.fcareer.tacticanalysis.TacticContext.Side;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/** 7 takım metriği (13 §3.4): hücum/savunma, genişlik, uzunluk, dayanıklılık, build-up, geri savunma, fırsat kanalları. Roller seed'den, oyuncu varsa attribute'larla birleşir. */
@Component
public class MetricsCalculator {

    private static final Set<String> BUILD_UP_GROUP = Set.of("GK", "CB", "CDM");
    private static final Set<String> CENTRAL_POSITIONS = Set.of("CDM", "CM", "CAM");
    private static final Set<String> WIDE_POSITIONS = Set.of("LM", "RM", "LW", "RW");
    private static final Set<String> CREATIVE_POSITIONS = Set.of("CM", "CDM", "CAM", "LM", "RM");
    private static final double PERCENT = 100.0;
    private static final double HALF_FIELD = 50.0;

    public Metrics calculate(TacticContext ctx) {
        List<Placed> out = ctx.outfield();
        double attack = out.stream().mapToDouble((p) -> p.m().r()).sum();
        double defence = out.stream().mapToDouble((p) -> p.m().d()).sum();
        double width = out.stream().mapToDouble((p) -> Math.abs(p.xBall() - HALF_FIELD)).average().orElse(0) / HALF_FIELD * PERCENT;
        int left = (int) out.stream().filter((p) -> p.xBall() < ctx.params().wingLeftMax()).count();
        int right = (int) out.stream().filter((p) -> p.xBall() > ctx.params().wingRightMin()).count();
        double withBall = spread(out, Placed::yBall);
        double withoutBall = spread(out, Placed::yNoBall);
        return new Metrics(ctx.band().name(), ctx.depth(), new AttackDefence(round(attack), round(defence), round(PERCENT * attack / Math.max(attack + defence, 1e-9))),
                new Width(round(width), left, right), new Length(round(withBall), round(withoutBall), round((withBall + withoutBall) / 2)), stamina(ctx), buildUp(ctx), restDefence(ctx),
                opportunity(ctx));
    }

    private static double spread(List<Placed> out, ToDoubleFunction<Placed> y) {
        return out.stream().mapToDouble(y).max().orElse(0) - out.stream().mapToDouble(y).min().orElse(0);
    }

    private Stamina stamina(TacticContext ctx) {
        double demand = ctx.outfield().stream().mapToDouble((p) -> p.m().s()).average().orElse(0) * ctx.pressMult();
        OptionalDouble avg = ctx.outfield().stream().filter((p) -> p.player() != null).mapToDouble((p) -> p.player().attr(Attr.STAMINA)).average();
        if (avg.isEmpty()) {
            return new Stamina(round3(demand), null, null);
        }
        double supply = Math.min(1, Math.max(0, (avg.getAsDouble() - ctx.params().staminaSupplyFrom()) / ctx.params().staminaSupplySpan()));
        return new Stamina(round3(demand), round3(supply), round3(demand - supply));
    }

    private BuildUp buildUp(TacticContext ctx) {
        double required = ctx.params().buildUpRequired().get(ctx.buildUp());
        OptionalDouble safety = buildUpSafety(ctx);
        return new BuildUp(ctx.buildUp().name(), safety.isPresent() ? round(safety.getAsDouble()) : null, required, safety.isPresent() ? round(safety.getAsDouble() - required) : null);
    }

    /** GK + CB + CDM oyuncularının ortalama (Short Passing, Composure, Ball Control, Vision). */
    public OptionalDouble buildUpSafety(TacticContext ctx) {
        return ctx.placed().stream().filter((p) -> p.player() != null && BUILD_UP_GROUP.contains(p.position()))
                .mapToDouble((p) -> (p.player().attr(Attr.SHORT_PASSING) + p.player().attr(Attr.COMPOSURE) + p.player().attr(Attr.BALL_CONTROL) + p.player().attr(Attr.VISION)) / 4.0).average();
    }

    public RestDefence restDefence(TacticContext ctx) {
        List<String> slots = ctx.outfield().stream().filter((p) -> p.m().d() >= ctx.params().restDefenceDMin() && p.m().dyFwd() <= ctx.params().restDefenceDyMax()).map(Placed::slotId).toList();
        return new RestDefence(slots.size(), slots);
    }

    /**
     * Merkezi orta saha sayısı (topsuz konumla): CDM/CM/CAM slotları (x 30-70) + içe kesen kanatlar (dxIn >= eşik; topla x 30-70). Roller sayıyı değiştirmez, formasyon ve içe kesme değiştirir.
     */
    public long centralMidfieldCount(TacticContext ctx) {
        TacticParams p = ctx.params();
        return ctx.outfield().stream().filter((s) -> {
            boolean central = CENTRAL_POSITIONS.contains(s.position()) && s.x() >= p.midfieldXMin() && s.x() <= p.midfieldXMax();
            boolean cutsIn = WIDE_POSITIONS.contains(s.position()) && s.m().dxIn() >= p.midfieldCutInDxMin() && s.xBall() >= p.midfieldXMin() && s.xBall() <= p.midfieldXMax();
            return central || cutsIn;
        }).count();
    }

    private Opportunity opportunity(TacticContext ctx) {
        List<Placed> out = ctx.outfield();
        double crossVolume = out.stream().mapToDouble((p) -> p.m().cross()).sum();
        double cutInVolume = out.stream().mapToDouble((p) -> p.m().cutIn()).sum();
        double runVolume = out.stream().mapToDouble((p) -> p.m().runBehind()).sum();
        double shotVolume = out.stream().mapToDouble((p) -> p.m().longShot()).sum();
        return new Opportunity(channel(crossVolume, crossQuality(ctx)), channel(cutInVolume, cutInQuality(ctx)), channel(runVolume, runBehindQuality(ctx)), channel(shotVolume, longShotQuality(ctx)));
    }

    private static Channel channel(double volume, OptionalDouble quality) {
        return quality.isPresent() ? new Channel(round(volume), round(quality.getAsDouble()), round(volume * quality.getAsDouble() / PERCENT)) : new Channel(round(volume), null, null);
    }

    /** Orta kanalı: orta kalitesi (Crossing, orta eğilimiyle ağırlıklı) x hava hedefleri (Heading, Jumping, boy). */
    public OptionalDouble crossQuality(TacticContext ctx) {
        OptionalDouble crossing = weighted(ctx, (p) -> p.m().cross(), (p) -> p.m().cross() >= ctx.params().crossCrosserMin(), (p) -> p.player().attr(Attr.CROSSING));
        OptionalDouble aerial = aerialQuality(ctx);
        return crossing.isPresent() && aerial.isPresent() ? OptionalDouble.of(crossing.getAsDouble() * aerial.getAsDouble() / PERCENT) : OptionalDouble.empty();
    }

    /** Hava hedefi: ileri koşusu yüksek (r >= eşik) saha oyuncularının (Heading + Jumping + boy skoru) / 3 ortalaması, r ile ağırlıklı. */
    public OptionalDouble aerialQuality(TacticContext ctx) {
        return weighted(ctx, (p) -> p.m().r(), (p) -> p.m().r() >= ctx.params().crossTargetRMin(),
                (p) -> (p.player().attr(Attr.HEADING) + p.player().attr(Attr.JUMPING) + heightScore(ctx, p.player())) / 3.0);
    }

    private static double heightScore(TacticContext ctx, FitPlayer player) {
        if (Objects.isNull(player.getHeightCm())) {
            return HALF_FIELD;
        }
        return Math.min(1, Math.max(0, (player.getHeightCm() - ctx.params().heightMin()) / (ctx.params().heightMax() - ctx.params().heightMin()))) * PERCENT;
    }

    /** İçe kat: Finishing x ayak (içe keseceği ayak güçlü ayaksa tam, değilse offFootFactor), cutIn ile ağırlıklı. */
    public OptionalDouble cutInQuality(TacticContext ctx) {
        return weighted(ctx, (p) -> p.m().cutIn(), (p) -> p.m().cutIn() > 0, (p) -> p.player().attr(Attr.FINISHING) * footFactor(ctx, p));
    }

    private static double footFactor(TacticContext ctx, Placed p) {
        String foot = Objects.isNull(p.player().getPreferredFoot()) ? "" : p.player().getPreferredFoot().toUpperCase(Locale.ROOT);
        if (p.side() == Side.CENTER || foot.isEmpty()) {
            return 1.0;
        }
        boolean matches = p.side() == Side.LEFT ? foot.startsWith("R") : foot.startsWith("L");
        return matches ? 1.0 : ctx.params().offFootFactor();
    }

    /** Arkaya koşu: pas atan Vision (en iyi yaratıcı) x koşucuların pace'i (runBehind ağırlıklı Acceleration+SprintSpeed). */
    public OptionalDouble runBehindQuality(TacticContext ctx) {
        OptionalDouble vision = ctx.placed().stream().filter((p) -> p.player() != null && CREATIVE_POSITIONS.contains(p.position())).mapToDouble((p) -> p.player().attr(Attr.VISION)).max();
        OptionalDouble pace = weighted(ctx, (p) -> p.m().runBehind(), (p) -> p.m().runBehind() > 0, (p) -> (p.player().attr(Attr.ACCELERATION) + p.player().attr(Attr.SPRINT_SPEED)) / 2.0);
        return vision.isPresent() && pace.isPresent() ? OptionalDouble.of(vision.getAsDouble() * pace.getAsDouble() / PERCENT) : OptionalDouble.empty();
    }

    public OptionalDouble longShotQuality(TacticContext ctx) {
        return weighted(ctx, (p) -> p.m().longShot(), (p) -> p.m().longShot() > 0, (p) -> p.player().attr(Attr.LONG_SHOTS));
    }

    private static OptionalDouble weighted(TacticContext ctx, ToDoubleFunction<Placed> weight, Predicate<Placed> include, ToDoubleFunction<Placed> value) {
        double sum = 0;
        double total = 0;
        for (Placed p : ctx.outfield()) {
            if (p.player() != null && include.test(p) && weight.applyAsDouble(p) > 0) {
                sum += weight.applyAsDouble(p) * value.applyAsDouble(p);
                total += weight.applyAsDouble(p);
            }
        }
        return total > 0 ? OptionalDouble.of(sum / total) : OptionalDouble.empty();
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
