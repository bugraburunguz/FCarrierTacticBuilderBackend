package com.fcareer.fitengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.norms.AttrNorms;
import com.fcareer.fitengine.norms.PosDist;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** E3-3 CI değişmezleri (13 §3.5 "veri değişmezleri"): attr-norms ve pos-dist seed'leri. */
class FitSeedInvariantsTest {

    private static final List<String> POSITIONS = List.of("GK", "CB", "LB", "RB", "CDM", "CM", "CAM", "LM", "RM", "LW", "RW", "ST");

    private final AttrNorms norms = new AttrNorms(new ObjectMapper());
    private final PosDist posDist = new PosDist(new ObjectMapper());

    @Test
    void theSeedHashesMatchTheirContent() throws Exception {
        assertThat(norms.hash()).isEqualTo(norms.expectedHash());
        assertThat(posDist.hash()).isEqualTo(posDist.expectedHash());
    }

    @Test
    void everyMenPositionCarriesAll34AttributesWithOrderedPercentilesAndPositiveSpread() {
        for (String position : POSITIONS) {
            assertThat(norms.playerCount(0, position)).as(position).isGreaterThanOrEqualTo(norms.minPlayers());
            for (Attr attr : Attr.values()) {
                AttrNorms.Norm n = norms.norm(0, position, attr).orElseThrow(() -> new AssertionError(position + " " + attr));
                assertThat(n.p10()).as(position + " " + attr + " p10≤p50").isLessThanOrEqualTo(n.p50());
                assertThat(n.p50()).as(position + " " + attr + " p50≤p90").isLessThanOrEqualTo(n.p90());
                assertThat(n.sd()).as(position + " " + attr + " sd>0").isGreaterThan(0);
            }
        }
    }

    @Test
    void utilityIsAHalfAtTheMedianAndStrictlyIncreasing() {
        double atMedian = norms.utility(0, "ST", Attr.FINISHING, norms.norm(0, "ST", Attr.FINISHING).orElseThrow().p50());
        assertThat(atMedian).isBetween(0.49, 0.51);
        double previous = -1;
        for (int value = 20; value <= 99; value += 5) {
            double u = norms.utility(0, "ST", Attr.FINISHING, value);
            assertThat(u).isGreaterThan(previous);
            previous = u;
        }
    }

    @Test
    void aGroupWithTooFewPlayersFallsBackToTheMenValues() {
        // Kadın LW/RW yeterli oyuncu yok → erkek normları kullanılır
        assertThat(norms.playerCount(1, "LW")).isZero();
        assertThat(norms.norm(1, "LW", Attr.DRIBBLING)).isEqualTo(norms.norm(0, "LW", Attr.DRIBBLING));
    }

    @Test
    void theOneAttributePointMovesTheUtilityByAtMostOneAndAHalfPoints() {
        // 13 §2 madde 4 (uçurum yok): 1 attribute puanı u'yu en fazla 0,015 oynatır (100 ölçeğinde 1,5)
        for (String position : POSITIONS) {
            for (Attr attr : Attr.values()) {
                for (int value = 30; value < 95; value += 7) {
                    double delta = norms.utility(0, position, attr, value + 1) - norms.utility(0, position, attr, value);
                    assertThat(delta).as(position + " " + attr + " @" + value).isBetween(0.0, 0.1);
                }
            }
        }
    }

    @Test
    void posDistMatrixIsCompleteBoundedAndKeepsTheDocumentedPairs() {
        for (String a : POSITIONS) {
            assertThat(posDist.factor(a, a)).as(a).isEqualTo(1.0);
            for (String b : POSITIONS) {
                assertThat(posDist.factor(a, b)).as(a + "→" + b).isBetween(0.0, 1.0);
            }
        }
        assertThat(posDist.factor("RB", "LB")).isEqualTo(0.97);
        assertThat(posDist.factor("CM", "CDM")).isEqualTo(0.93);
        assertThat(posDist.factor("CB", "CDM")).isEqualTo(0.9);
        assertThat(posDist.factor("CAM", "ST")).isEqualTo(0.9);
        assertThat(posDist.factor("RW", "RM")).isEqualTo(0.97);
    }

    @Test
    void anOutOfPositionFactorNeverBeatsTheNaturalOneAndGoalkeepersAreNotSwapped() {
        for (String a : POSITIONS) {
            for (String b : POSITIONS) {
                if (!a.equals(b)) {
                    assertThat(posDist.factor(a, b)).as(a + "→" + b).isLessThan(1.0);
                }
            }
        }
        for (String outfield : POSITIONS.subList(1, POSITIONS.size())) {
            assertThat(posDist.factor("GK", outfield)).isZero();
            assertThat(posDist.factor(outfield, "GK")).isZero();
        }
    }

    @Test
    void pairsOutsideTheDocumentedListStayAtOrBelowSeventyFivePercent() {
        List<String> documented = List.of("RB-LB", "LB-RB", "CM-CDM", "CDM-CM", "CB-CDM", "CAM-ST", "ST-CAM", "RW-RM", "RM-RW", "LW-LM", "LM-LW", "LM-RM", "RM-LM", "LW-RW", "RW-LW", "CM-CAM", "CAM-CM");
        for (String a : POSITIONS) {
            for (String b : POSITIONS) {
                if (!a.equals(b) && !documented.contains(a + "-" + b)) {
                    assertThat(posDist.factor(a, b)).as(a + "→" + b).isLessThanOrEqualTo(0.75);
                }
            }
        }
    }

    @Test
    void bothSeedsAreMarkedUnverified() {
        assertThat(norms.verified()).isFalse();
        assertThat(posDist.verified()).isFalse();
    }
}
