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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * FAZ 6a-E: zaman çizelgesi "İçe aktarma · 21668 oyuncu" (dünya oyuncu sayısı) yerine kadroyu anlatır: anlık görüntü o anki kadro sayısını
 * ve bir önceki görüntüye göre kadroya giren/çıkan oyuncuları taşır. Eski görüntüde kadro bilgisi yoksa alan yok (uydurulmaz).
 */
@IntegrationTest
class SnapshotSquadIntegrationTest {

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
    void snapshotsCarryTheSquadSizeAndWhoJoinedOrLeft() throws Exception {
        String token = call(post("/api/v1/auth/register"), Map.of("email", "snap-" + UUID.randomUUID() + "@example.com", "password", "Sup3rSecret!", "privacyVersion", 1), null, 200)
                .at("/data/accessToken").asText();
        long clubId = clubRepository.findAll().get(0).getId();
        long careerId = call(post("/api/v1/careers"), Map.of("clubId", clubId, "budgetEur", 50_000_000), token, 200).at("/data/id").asLong();
        JsonNode squad = call(get("/api/v1/careers/" + careerId + "/squad"), null, token, 200).at("/data");
        int size = squad.size();
        long first = squad.get(0).at("/player/id").asLong();
        String firstName = squad.get(0).at("/player/name").asText();

        call(put("/api/v1/careers/" + careerId + "/players/ratings"), Map.of("updates", List.of(Map.of("playerId", first, "overall", 80))), token, 200);
        call(post("/api/v1/careers/" + careerId + "/events"), Map.of("type", "SELL", "playerId", first, "feeEur", 1_000_000), token, 200);
        call(put("/api/v1/careers/" + careerId + "/players/ratings"), Map.of("updates", List.of(Map.of("playerId", squad.get(1).at("/player/id").asLong(), "overall", 81))), token, 200);

        JsonNode snapshots = call(get("/api/v1/careers/" + careerId + "/snapshots"), null, token, 200).at("/data");
        JsonNode latest = snapshots.get(0);
        JsonNode older = snapshots.get(1);
        assertThat(older.at("/squadCount").asInt()).isEqualTo(size);
        assertThat(older.has("squadJoined")).as("önceki görüntü yokken giren/çıkan yok").isFalse();
        assertThat(latest.at("/squadCount").asInt()).isEqualTo(size - 1);
        assertThat(latest.at("/squadLeft").toString()).contains(firstName);
        assertThat(latest.at("/squadJoined").size()).isZero();
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
