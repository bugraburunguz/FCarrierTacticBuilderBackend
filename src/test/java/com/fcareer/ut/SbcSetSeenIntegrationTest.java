package com.fcareer.ut;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.IntegrationTest;
import com.fcareer.catalog.configuration.CatalogProperties;
import com.fcareer.ut.kb.KnowledgeBaseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * V3-05c (API CONTRACT CHANGE): sbc_set.first_seen_at / content_changed_at (V35) ve upsert davranışı; "Yeni"/"Değişti" rozetlerinin verisi.
 * Paylaşılan context: yalnız kendi yüksek kimlikli satırlarını kullanır ve sonunda geri alır.
 */
@IntegrationTest
class SbcSetSeenIntegrationTest {

    private static final long SET = 7_700_001L;
    private static final long STUB_SET = 7_700_002L;
    private static final long RESOURCE = 7_700_003L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private KnowledgeBaseRepository repository;
    @Autowired
    private CatalogProperties catalogProperties;

    private short gv;

    @BeforeEach
    void setUp() {
        cleanUp();
        gv = catalogProperties.getDefaultGameVersion();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM sbc_challenge WHERE set_id IN (?, ?)", SET, STUB_SET);
        jdbc.update("DELETE FROM sbc_set WHERE id IN (?, ?)", SET, STUB_SET);
        jdbc.update("DELETE FROM ut_item WHERE ea_asset_id = ?", RESOURCE);
    }

    private void upsert(long id, String hash) {
        repository.upsertSbcSet(id, gv, "Seen Set", null, "Special", 1, Instant.now().plus(3, ChronoUnit.DAYS), objectMapper.createArrayNode(), "CONFIRMED", hash);
    }

    private Instant column(long id, String name) {
        Timestamp ts = jdbc.queryForObject("SELECT " + name + " FROM sbc_set WHERE id = ?", Timestamp.class, id);
        return ts == null ? null : ts.toInstant();
    }

    private JsonNode listed(long id) throws Exception {
        String body = mockMvc.perform(get("/api/v1/ut/kb/sbc")).andReturn().getResponse().getContentAsString();
        return StreamSupport.stream(objectMapper.readTree(body).path("data").spliterator(), false).filter((n) -> n.path("id").asLong() == id).findFirst().orElseThrow();
    }

    @Test
    void firstInsertSetsFirstSeenAndNoContentChange() throws Exception {
        upsert(SET, "h1");

        assertThat(column(SET, "first_seen_at")).isCloseTo(Instant.now(), org.assertj.core.api.Assertions.within(1, ChronoUnit.MINUTES));
        assertThat(column(SET, "content_changed_at")).isNull();
        JsonNode dto = listed(SET);
        assertThat(dto.path("firstSeenAt").isTextual()).isTrue();
        assertThat(dto.has("contentChangedAt")).isFalse();
    }

    @Test
    void sameHashKeepsTimestampsChangedHashMarksContentChange() throws Exception {
        upsert(SET, "h1");
        jdbc.update("UPDATE sbc_set SET first_seen_at = now() - interval '5 days' WHERE id = ?", SET);
        Instant firstSeen = column(SET, "first_seen_at");

        upsert(SET, "h1");
        assertThat(column(SET, "content_changed_at")).isNull();
        assertThat(column(SET, "first_seen_at")).isEqualTo(firstSeen);

        upsert(SET, "h2");
        assertThat(column(SET, "content_changed_at")).isCloseTo(Instant.now(), org.assertj.core.api.Assertions.within(1, ChronoUnit.MINUTES));
        assertThat(column(SET, "first_seen_at")).isEqualTo(firstSeen);
        assertThat(listed(SET).path("contentChangedAt").isTextual()).isTrue();
    }

    @Test
    void stubFilledWithRealContentIsNotAContentChange() {
        repository.ensureSetStub(STUB_SET, gv, "Stub");
        upsert(STUB_SET, "real");

        assertThat(column(STUB_SET, "content_changed_at")).isNull();
        assertThat(column(STUB_SET, "first_seen_at")).isNotNull();
    }

    @Test
    void itemsCanBeLookedUpByEaResourceId() throws Exception {
        jdbc.update("INSERT INTO ut_item (game_version_id, ea_asset_id, ea_resource_id, name, rating, position, source) VALUES (?, ?, ?, 'Ödül Oyuncu', 88, 'ST', 'ADMIN')",
                gv, RESOURCE, RESOURCE);
        // süzgeç gerçekten uygulanmalı: aynı oyuncunun başka kaynak kimlikli kartı listeye girmez
        jdbc.update("INSERT INTO ut_item (game_version_id, ea_asset_id, ea_resource_id, name, rating, position, source) VALUES (?, ?, ?, 'Başka Kart', 90, 'ST', 'ADMIN')",
                gv, RESOURCE, RESOURCE + 100_000_000L);

        String body = mockMvc.perform(get("/api/v1/ut/kb/items").param("resource", String.valueOf(RESOURCE), "1")).andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).path("data");

        assertThat(data).hasSize(1);
        assertThat(data.get(0).path("eaResourceId").asLong()).isEqualTo(RESOURCE);
    }
}
