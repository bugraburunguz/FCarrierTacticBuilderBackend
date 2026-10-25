package com.fcareer.development;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.development.plan.DevelopmentPlanReport;
import com.fcareer.development.plan.DevelopmentPlanReport.Report;
import com.fcareer.development.plan.DevelopmentPlanReport.Row;
import com.fcareer.development.plan.DevelopmentPlans;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.norms.RoleSeeds;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FIT-3 madde 8: plan ↔ vektör doğrulama raporu (otomatik değişiklik yok). Mevki planı ↔ mevki vektörünün en ağır 10'u; rol planı ↔ (mevki, rol) vektörünün
 * en ağır 10'u (odakların ortalaması); rolün farkı: extraVsPosition ↔ rol vektörünün mevki vektöründen en çok ayrıştığı attribute'lar.
 */
class DevelopmentPlanReportTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final DevelopmentPlanReport report = new DevelopmentPlanReport(new DevelopmentPlans(MAPPER), new RoleSeeds(MAPPER));

    @Test
    void everyPlanPositionGetsARowWithConsistentSets() {
        Report r = report.build();
        assertThat(r.positions()).hasSize(11);
        assertThat(r.roles()).as("rol planı × mevki (winger LM/RM → 2 satır)").hasSizeGreaterThan(37);
        for (Row row : r.positions()) {
            Set<Attr> union = new HashSet<>(row.common());
            union.addAll(row.onlyPlan());
            assertThat(union).as(row.plan()).containsExactlyInAnyOrderElementsOf(row.planAttributes());
            assertThat(row.top10()).hasSizeLessThanOrEqualTo(10).containsAll(row.common()).containsAll(row.onlyVector());
            assertThat(row.overlap()).isBetween(0.0, 1.0);
        }
        Row st = r.positions().stream().filter((x) -> x.position().equals("ST")).findFirst().orElseThrow();
        assertThat(st.common()).contains(Attr.FINISHING);
        assertThat(r.differences()).allSatisfy((d) -> assertThat(d.divergent()).doesNotHaveDuplicates());
    }

    @Test
    void goalkeeperRolePlansHaveNoPositionPlanSoAllTheirAttributesAreExtra() {
        assertThat(report.build().differences()).filteredOn((d) -> d.position().equals("GK")).allSatisfy((d) -> assertThat(d.extra()).isNotEmpty());
    }

    @Test
    void markdownListsTheMismatchTables() {
        String md = report.markdown(report.build());
        assertThat(md).contains("| Mevki planı").contains("| Rol planı").contains("| Rolün farkı").contains("pos_st");
    }
}
