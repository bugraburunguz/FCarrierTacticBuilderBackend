package com.fcareer.recommendation.pipeline;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** E3-6 fizibilite kovaları: ALINABILIR / ZOR / HAYAL; her karar sayısal gerekçeli (değer/bütçe, lig seviye farkı, kulüpteki rol, sözleşme). */
class FeasibilityBucketsTest {

    private final FeasibilityBuckets buckets = new FeasibilityBuckets(new RecommendProperties());

    private static FeasibilityBuckets.Input input(Double ratio, Integer tierGap, Integer clubRank, Integer contractMonths, boolean free) {
        return new FeasibilityBuckets.Input(ratio, tierGap, clubRank, contractMonths, free);
    }

    @Test
    void aFreeAgentIsAlwaysTakeableAndSaysWhy() {
        FeasibilityBuckets.Result r = buckets.classify(input(5.0, -3, 1, 36, true));
        assertThat(r.bucket()).isEqualTo(FeasibilityBucket.ALINABILIR);
        assertThat(r.factors()).anyMatch((f) -> f.name().equals("serbest") && f.points() == 0);
    }

    @Test
    void cheapPlayerInASimilarLeagueWhoIsNotAKeyPlayerIsTakeable() {
        FeasibilityBuckets.Result r = buckets.classify(input(0.4, 0, 20, null, false));
        assertThat(r.bucket()).isEqualTo(FeasibilityBucket.ALINABILIR);
        assertThat(r.points()).isZero();
    }

    @Test
    void aPricyKeyPlayerOfABetterLeagueIsAFantasy() {
        FeasibilityBuckets.Result r = buckets.classify(input(3.5, -2, 2, 48, false));
        assertThat(r.bucket()).isEqualTo(FeasibilityBucket.HAYAL);
        assertThat(r.points()).isGreaterThan(3);
    }

    @Test
    void middlingCasesAreHard() {
        FeasibilityBuckets.Result r = buckets.classify(input(1.5, -1, 8, null, false));
        assertThat(r.bucket()).isEqualTo(FeasibilityBucket.ZOR);
    }

    @Test
    void anUnknownBudgetOrValueNeverCountsAgainstThePlayer() {
        FeasibilityBuckets.Result r = buckets.classify(input(null, 0, 20, null, false));
        assertThat(r.bucket()).isEqualTo(FeasibilityBucket.ALINABILIR);
        assertThat(r.factors()).anyMatch((f) -> f.name().equals("değer/bütçe") && f.points() == 0 && f.note().contains("bilinmiyor"));
    }

    @Test
    void anExpiringContractMakesAPlayerEasierButNeverBelowZeroPoints() {
        FeasibilityBuckets.Result easier = buckets.classify(input(1.5, -1, 8, 4, false));
        FeasibilityBuckets.Result normal = buckets.classify(input(1.5, -1, 8, null, false));
        assertThat(easier.points()).isLessThan(normal.points());
        assertThat(buckets.classify(input(0.1, 0, 30, 2, false)).points()).isZero();
    }

    @Test
    void everyFactorCarriesTheNumberBehindIt() {
        FeasibilityBuckets.Result r = buckets.classify(input(2.4, -1, 5, 40, false));
        assertThat(r.factors()).extracting(FeasibilityBuckets.Factor::name).contains("değer/bütçe", "lig seviyesi", "kulüpteki rol", "sözleşme");
        r.factors().forEach((f) -> assertThat(f.note()).isNotBlank());
    }
}
