package com.fcareer.roles;

import com.fcareer.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class FiveThreeTwoSlotMigrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void personalPresetCmSlotMovesToCdm() throws Exception {
        jdbc.update("DELETE FROM preset WHERE id = 'tmp_personal_532'");
        jdbc.update("INSERT INTO preset (id, kind, name, formation, slot_tags, role_hints) VALUES ('tmp_personal_532', 'PERSONAL', 'tmp', '5-3-2', "
                + "'{\"CM\":[\"cm_deep_build\",\"cm_late_run\"],\"LCM\":[\"cm_engine\"]}'::jsonb, '{\"CM\":\"Geriden kuran\"}'::jsonb)");
        String sql = new ClassPathResource("db/migration/V18__five_three_two_cdm_slot.sql").getContentAsString(StandardCharsets.UTF_8);

        jdbc.execute(sql);

        assertThat(jdbc.queryForObject("SELECT slot_tags::text FROM preset WHERE id = 'tmp_personal_532'", String.class))
                .contains("\"CDM\": [\"cdm_deep_build\"]").doesNotContain("\"CM\":").contains("\"LCM\": [\"cm_engine\"]");
        assertThat(jdbc.queryForObject("SELECT role_hints::text FROM preset WHERE id = 'tmp_personal_532'", String.class)).contains("\"CDM\": \"Geriden kuran\"");
        jdbc.update("DELETE FROM preset WHERE id = 'tmp_personal_532'");
    }
}
