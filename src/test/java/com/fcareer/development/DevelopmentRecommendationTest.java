package com.fcareer.development;

import com.fcareer.development.recommend.DevelopmentRecommender;
import com.fcareer.development.recommend.DevelopmentRecommender.Option;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FIT-3 madde 11: her özel plan Dengeli'ye karşı aynı hedefte (mevki, rol) kıyaslanır. En büyük fark eşiğin (2 Güç, verified:false) altındaysa
 * Dengeli önerilir. Metin "tahmin" olarak okunur: Güç 2 sezon sonrası.
 */
class DevelopmentRecommendationTest {

    private static Option option(String plan, String kind, double strength2, double balanced2) {
        return new Option(plan, plan, kind, "CDM", "holding_defend", "Holding (Defend)", strength2 - 2, strength2, 55.0, balanced2, strength2 - balanced2, null, null);
    }

    @Test
    void bestSpecialPlanWinsWhenItBeatsBalancedByTheThreshold() {
        List<Option> options = List.of(option("role:holding:CDM", "ROLE", 71, 67), option("pos_cdm", "POSITION", 69, 67));
        Option chosen = DevelopmentRecommender.choose(options, 2.0).orElseThrow();
        assertThat(chosen.planKey()).isEqualTo("role:holding:CDM");
    }

    @Test
    void belowThresholdBalancedIsRecommended() {
        List<Option> options = List.of(option("role:holding:CDM", "ROLE", 68, 67), option("pos_cdm", "POSITION", 68.5, 67));
        assertThat(DevelopmentRecommender.choose(options, 2.0)).as("en çok +1,5 → Dengeli").isEmpty();
    }

    private static Option at(String plan, String position, double strength2, double balanced2) {
        return new Option(plan, plan, "ROLE", position, "r", "Rol", strength2 - 2, strength2, 55.0, balanced2, strength2 - balanced2, null, null);
    }

    /** Karar: 2 sezon sonraki en iyi rol, farklı mevkideki ikinci en iyiden ≥ 5 öndeyse "net hedef" → o rol planı; değilse Dengeli + gerekçe. */
    @Test
    void clearTargetWinsOtherwiseBalancedWithNoClearPositionReason() {
        List<Option> clear = List.of(at("role:holding:CDM", "CDM", 71, 60), at("role:holding:CM", "CM", 68, 60), at("role:winger:LM", "LM", 65, 60));
        DevelopmentRecommender.Decision d = DevelopmentRecommender.decide(clear, 2.0, 5.0);
        assertThat(d.chosen()).as("CDM 71, farklı mevkide en iyi CM 68 → fark 3 < 5").isNull();
        assertThat(d.reason()).isEqualTo("NO_CLEAR_TARGET");
        List<Option> net = List.of(at("role:holding:CDM", "CDM", 71, 60), at("role:box_crasher:CDM", "CDM", 70, 60), at("role:holding:CM", "CM", 65, 60));
        DevelopmentRecommender.Decision n = DevelopmentRecommender.decide(net, 2.0, 5.0);
        assertThat(n.chosen().planKey()).isEqualTo("role:holding:CDM");
        assertThat(n.reason()).isEqualTo("CLEAR_TARGET");
        assertThat(DevelopmentRecommender.decide(List.of(at("role:holding:CDM", "CDM", 71, 70)), 2.0, 5.0).reason()).as("tek mevki ama Dengeli'den +1").isEqualTo("SMALL_GAIN");
        // Metin kararın dayandığı hedefin farkını yazar (en büyük farkı değil): en güçlü hedef CDM 71 (+1), başka CDM planı +10 olsa da.
        DevelopmentRecommender.Decision small = DevelopmentRecommender.decide(List.of(at("role:holding:CDM", "CDM", 71, 70), at("role:box_crasher:CDM", "CDM", 66, 56)), 2.0, 5.0);
        assertThat(small.reason()).isEqualTo("SMALL_GAIN");
        assertThat(small.best().gain()).isEqualTo(1.0);
        assertThat(DevelopmentRecommender.noClearTargetText("Genç")).isEqualTo("Genç · Dengeli plan: Henüz net bir mevkisi yok, esnek gelişsin");
    }

    @Test
    void textReadsLikeTheBrief() {
        Option chosen = new Option("role:deep_lying_playmaker:CDM", "Deep-Lying Playmaker", "ROLE", "CDM", "deep_lying_playmaker_build_up",
                "Deep-Lying Playmaker (Build-Up)", 69, 71.2, 58.0, 67.4, 3.8, "Nyoni", 68.1);
        assertThat(DevelopmentRecommender.text("Figueiredo", chosen))
                .isEqualTo("Figueiredo · CDM Deep-Lying Playmaker planı → 2 sezonda CDM gücü 71 (Dengeli: 67; Nyoni'nin tahmini 68)");
        assertThat(DevelopmentRecommender.balancedText("Figueiredo", 1.4)).isEqualTo("Figueiredo · Dengeli plan yeterli: en güçlü hedefte özel plan 2 sezonda yalnız +1 Güç getiriyor");
    }
}
