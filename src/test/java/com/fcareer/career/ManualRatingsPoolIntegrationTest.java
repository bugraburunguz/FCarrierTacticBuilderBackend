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
 * REVIEW K1 (Kritik): konsol oyuncusunun (LE içe aktarması olmayan kariyer) elle OVR güncellemesi oyuncuyu "canlı kayıt" yapıp havuzu
 * yalnız güncellenen oyunculara indiriyordu (öneri, kulüp gücü boşalıyordu) ve oyuncunun kulübünü siliyordu; anlık görüntü 0 oyuncu yazıyordu.
 */
@IntegrationTest
class ManualRatingsPoolIntegrationTest {

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
    void manualRatingsKeepTheWholePoolTheClubAndAFullSnapshot() throws Exception {
        String token = call(post("/api/v1/auth/register"), Map.of("email", "manual-" + UUID.randomUUID() + "@example.com", "password", "Sup3rSecret!", "privacyVersion", 1), null, 200)
                .at("/data/accessToken").asText();
        long clubId = clubRepository.findAll().get(0).getId();
        long careerId = call(post("/api/v1/careers"), Map.of("clubId", clubId, "budgetEur", 5_000_000), token, 200).at("/data/id").asLong();
        long poolBefore = call(get("/api/v1/players?career=" + careerId + "&size=1"), null, token, 200).at("/data/totalItems").asLong();
        JsonNode squad = call(get("/api/v1/careers/" + careerId + "/squad"), null, token, 200).at("/data");
        long playerId = squad.get(0).at("/player/id").asLong();
        int clubBefore = squad.get(0).at("/player/clubId").asInt();

        JsonNode snapshot = call(put("/api/v1/careers/" + careerId + "/players/ratings"), Map.of("updates", List.of(Map.of("playerId", playerId, "overall", 88))), token, 200).at("/data");

        assertThat(call(get("/api/v1/players?career=" + careerId + "&size=1"), null, token, 200).at("/data/totalItems").asLong()).as("havuz daralmaz").isEqualTo(poolBefore);
        JsonNode after = call(get("/api/v1/careers/" + careerId + "/squad"), null, token, 200).at("/data");
        JsonNode updated = java.util.stream.StreamSupport.stream(after.spliterator(), false).filter(c -> c.at("/player/id").asLong() == playerId).findFirst().orElseThrow();
        assertThat(updated.at("/player/overall").asInt()).as("elle OVR uygulanır").isEqualTo(88);
        assertThat(updated.at("/player/clubId").asInt()).as("kulüp kaybolmaz").isEqualTo(clubBefore);
        assertThat(snapshot.at("/playerCount").asInt()).as("anlık görüntü boş değil").isPositive();
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
