package com.fcareer.catalog.repository;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** E3-10 bulgusu: canlı havuz filtresi satır başına tarama yapan korelasyonlu EXISTS olamaz (22 bin satırlık katalog kariyerinde ~55 sn sürdü). */
class LivePoolFilterTest {

    @Test
    void theLivePoolFilterDoesNotCorrelateWithTheOuterRow() {
        String filter = CareerSearchProfile.INSTANCE.livePoolFilter();
        assertThat(filter).doesNotContain("cp.career_id").doesNotContain("EXISTS").contains(":careerId");
    }
}
