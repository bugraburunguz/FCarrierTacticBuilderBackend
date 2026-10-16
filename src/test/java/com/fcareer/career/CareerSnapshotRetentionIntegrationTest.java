package com.fcareer.career;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.IntegrationTest;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.repository.ClubRepository;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** BE-BUG-14: saklama sınırı yapılandırılınca yalnız en yeni N snapshot kalır (varsayılan sınırsız: CareerImportIntegrationTest). */
@IntegrationTest
@TestPropertySource(properties = "fcareer.career.snapshot-retention=2")
class CareerSnapshotRetentionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private BasePlayerRepository basePlayerRepository;
    @Autowired
    private ClubRepository clubRepository;
    @Autowired
    private IngestionService ingestionService;

    @BeforeEach
    void seed() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
    }

    @Test
    void configuredRetentionKeepsOnlyTheNewestSnapshots() throws Exception {
        String token = call(post("/api/v1/auth/register"), Map.of("email", "keep-" + UUID.randomUUID() + "@example.com", "password", "Sup3rSecret!", "privacyVersion", 1), null, 200).at("/data/accessToken").asText();
        long clubId = clubRepository.findAll().get(0).getId();
        long careerId = call(post("/api/v1/careers"), Map.of("clubId", clubId, "budgetEur", 5_000_000), token, 200).at("/data/id").asLong();
        JsonNode first = call(get("/api/v1/careers/" + careerId + "/squad"), null, token, 200).at("/data/0/player");
        long playerId = first.at("/id").asLong();
        int overall = first.at("/overall").asInt();

        for (int i = 0; i < 4; i++) {
            call(put("/api/v1/careers/" + careerId + "/players/ratings"), Map.of("updates", List.of(Map.of("playerId", playerId, "overall", overall + i, "potential", overall + 5))), token, 200);
        }

        assertThat(call(get("/api/v1/careers/" + careerId + "/snapshots"), null, token, 200).at("/data")).hasSize(2);
    }

    private JsonNode call(MockHttpServletRequestBuilder builder, Object body, String token, int expectedStatus) throws Exception {
        builder.accept(MediaType.APPLICATION_JSON);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body));
        }
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        var result = mockMvc.perform(builder).andReturn().getResponse();
        assertThat(result.getStatus()).as(result.getContentAsString()).isEqualTo(expectedStatus);
        return objectMapper.readTree(result.getContentAsString());
    }
}
