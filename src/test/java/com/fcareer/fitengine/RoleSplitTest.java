package com.fcareer.fitengine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Java ve node öğrenicisi aynı bölmeyi kullanır (karşılaştırma değerleri node çıktısından). */
class RoleSplitTest {

    @Test
    void splitMatchesTheNodeLearnerForKnownIds() {
        long[] ids = {158023, 231747, 192119, 1, 2, 3, 4, 999999, 70675, 256630};
        int[] expected = {0, 0, 0, 1, 1, 1, 1, 0, 0, 1};
        for (int i = 0; i < ids.length; i++) {
            assertThat(RoleSplit.half(ids[i])).as("id " + ids[i]).isEqualTo(expected[i]);
        }
    }

    @Test
    void splitIsRoughlyBalancedOverRealisticIds() {
        int test = 0;
        for (long id = 150_000; id < 160_000; id++) {
            test += RoleSplit.half(id);
        }
        assertThat(test).isBetween(4_500, 5_500);
    }
}
