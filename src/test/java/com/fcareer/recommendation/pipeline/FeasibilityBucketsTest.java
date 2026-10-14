package com.fcareer.recommendation.pipeline;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fizibilite kovaları (E3-6b): ALINABİLİR / ZOR / HAYAL. Girdiler: değer/bütçe, hedef kulüp gücü − kullanıcı kulüp gücü, oyuncu OVR'si − kullanıcının ilk 11 ortalaması,
 * kulüpteki rol, sözleşme. "Serbest = alınabilir" kuralı YOK: serbest oyuncu da OVR farkı kuralına tabidir.
 */
class FeasibilityBucketsTest {

    private final FeasibilityBuckets buckets = new FeasibilityBuckets(new RecommendProperties());

    private static FeasibilityBuckets.Input input(Double ratio, Double clubGap, Double ovrGap, Integer clubRank, Integer contractMonths, boolean free) {
        return new FeasibilityBuckets.Input(ratio, clubGap, ovrGap, clubRank, contractMonths, free);
    }

    @Test
    void aFreeAgentWithinReachOfTheSquadLevelIsTakeable() {
        FeasibilityBuckets.Result r = buckets.classify(input(5.0, null, 2.0, null, null, true));
        assertThat(r.bucket()).isEqualTo(FeasibilityBucket.ALINABILIR);
        assertThat(r.factors()).anyMatch((f) -> f.name().equals("serbest"));
    }

    @Test
    void aFreeAgentFarAboveTheSquadLevelIsNotTakeableJustBecauseHeIsFree() {
        FeasibilityBuckets.Result r = buckets.classify(input(null, null, 14.0, null, null, true));
        assertThat(r.bucket()).isNotEqualTo(FeasibilityBucket.ALINABILIR);
        assertThat(r.factors()).anyMatch((f) -> f.name().equals("OVR farkı") && f.points() == 3);
    }

    @Test
    void cheapPlayerOfAClubOfTheSameStrengthAtTheSquadLevelIsTakeable() {
        FeasibilityBuckets.Result r = buckets.classify(input(0.4, 0.5, 1.0, 20, null, false));
        assertThat(r.bucket()).isEqualTo(FeasibilityBucket.ALINABILIR);
        assertThat(r.points()).isZero();
    }

    @Test
    void aStrongClubsKeyPlayerFarAboveTheSquadIsAFantasy() {
        FeasibilityBuckets.Result r = buckets.classify(input(3.5, 12.0, 15.0, 2, 48, false));
        assertThat(r.bucket()).isEqualTo(FeasibilityBucket.HAYAL);
        assertThat(r.points()).isGreaterThan(4);
    }

    @Test
    void middlingCasesAreHard() {
        FeasibilityBuckets.Result r = buckets.classify(input(0.8, 4.0, 5.0, 8, null, false));
        assertThat(r.bucket()).isEqualTo(FeasibilityBucket.ZOR);
    }

    @Test
    void aSmallClubPlayerWhoWouldDropDownALotIsHardEvenIfCheap() {
        FeasibilityBuckets.Result easy = buckets.classify(input(0.3, -4.0, 2.0, 15, null, false));
        FeasibilityBuckets.Result gap = buckets.classify(input(0.3, -4.0, 13.0, 15, null, false));
        assertThat(easy.bucket()).isEqualTo(FeasibilityBucket.ALINABILIR);
        assertThat(gap.bucket()).as("bu oyuncu bu seviyeye inmez").isNotEqualTo(FeasibilityBucket.ALINABILIR);
    }

    @Test
    void anUnknownBudgetOrValueNeverCountsAgainstThePlayer() {
        FeasibilityBuckets.Result r = buckets.classify(input(null, 0.0, 1.0, 20, null, false));
        assertThat(r.bucket()).isEqualTo(FeasibilityBucket.ALINABILIR);
        assertThat(r.factors()).anyMatch((f) -> f.name().equals("değer/bütçe") && f.points() == 0 && f.note().contains("bilinmiyor"));
    }

    @Test
    void anExpiringContractMakesAPlayerEasierButNeverBelowZeroPoints() {
        FeasibilityBuckets.Result easier = buckets.classify(input(0.8, 4.0, 5.0, 8, 4, false));
        FeasibilityBuckets.Result normal = buckets.classify(input(0.8, 4.0, 5.0, 8, null, false));
        assertThat(easier.points()).isLessThan(normal.points());
        assertThat(buckets.classify(input(0.1, 0.0, 0.0, 30, 2, false)).points()).isZero();
    }

    @Test
    void everyFactorCarriesTheNumberBehindIt() {
        FeasibilityBuckets.Result r = buckets.classify(input(2.4, 3.0, 6.0, 5, 40, false));
        assertThat(r.factors()).extracting(FeasibilityBuckets.Factor::name).contains("değer/bütçe", "kulüp gücü farkı", "OVR farkı", "kulüpteki rol", "sözleşme");
        r.factors().forEach((f) -> assertThat(f.note()).isNotBlank());
    }
}
