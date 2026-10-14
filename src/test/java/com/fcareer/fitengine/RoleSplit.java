package com.fcareer.fitengine;

/** E3-4b: oyuncu bazında sabit 50/50 bölme (karma: ((id * 2654435761) mod 2^32) >> 13 & 1). tools/fit-seeds/learn-role-vectors.mjs `half` ile birebir aynıdır. 0 = eğitim, 1 = test. */
final class RoleSplit {

    private static final long MULTIPLIER = 2_654_435_761L;
    private static final long MASK_32 = 0xffffffffL;
    private static final int SHIFT = 13;

    private RoleSplit() {
    }

    static int half(long playerId) {
        return (int) ((((playerId * MULTIPLIER) & MASK_32) >> SHIFT) & 1L);
    }
}
