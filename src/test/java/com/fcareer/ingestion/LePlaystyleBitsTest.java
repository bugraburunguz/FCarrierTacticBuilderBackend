package com.fcareer.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.ingestion.base.LePlaystyleBits;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** E3-2: LE trait1/trait2/icontrait1/icontrait2 bit maskeleri seed'ten (seed/le-playstyle-bits.json) çözülür; bilinmeyen bit unknown_<alan>_<N> olarak saklanır. */
class LePlaystyleBitsTest {

    private static final long MBAPPE_TRAIT1 = 6291681L;
    private static final long MBAPPE_ICONTRAIT1 = 33554432L;
    private static final long MBAPPE_TRAIT2 = 256L;

    private final LePlaystyleBits bits = new LePlaystyleBits(new ObjectMapper());

    @Test
    void mbappeDecodesToHisKnownPlaystylesWithQuickStepPlus() {
        LePlaystyleBits.Decoded d = bits.decode(MBAPPE_TRAIT1, MBAPPE_TRAIT2, MBAPPE_ICONTRAIT1, 0L);
        assertThat(d.playstyles()).containsOnly(Map.entry("FinesseShot", 1), Map.entry("Acrobatic", 1), Map.entry("LowDrivenShot", 1), Map.entry("Gamechanger", 1),
                Map.entry("Rapid", 1), Map.entry("FirstTouch", 1), Map.entry("QuickStep", 2));
        assertThat(d.hiddenTraits()).containsExactly("SolidPlayer");
        assertThat(d.unknown()).isEmpty();
    }

    @Test
    void goalkeeperPlusBitWithoutTheBaseBitStillCountsAsPlus() {
        LePlaystyleBits.Decoded d = bits.decode(0L, 17L, 0L, 8L);
        assertThat(d.playstyles()).containsOnly(Map.entry("FarThrow", 1), Map.entry("FarReach", 1), Map.entry("RushOut", 2));
    }

    @Test
    void bitsWithoutALocalizedNameAreKeptAsUnknown() {
        LePlaystyleBits.Decoded d = bits.decode(1L << 30, 1L << 14, 0L, 0L);
        assertThat(d.playstyles()).isEmpty();
        assertThat(d.unknown()).containsExactlyInAnyOrder("unknown_trait1_30", "unknown_trait2_14");
    }

    @Test
    void nullMasksDecodeToNothing() {
        LePlaystyleBits.Decoded d = bits.decode(null, null, null, null);
        assertThat(d.playstyles()).isEmpty();
        assertThat(d.hiddenTraits()).isEmpty();
    }
}
