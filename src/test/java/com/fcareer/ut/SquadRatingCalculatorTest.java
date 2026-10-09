package com.fcareer.ut;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.ut.service.SquadRatingCalculator;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SquadRatingCalculatorTest {

    @Test
    void matchesTheSharedTestVectors() throws Exception {
        JsonNode rules = new ObjectMapper().readTree(new ClassPathResource("seed/ut-rules.fc27.json").getInputStream());
        for (JsonNode vector : rules.get("ratingTestVectors")) {
            List<Integer> ratings = new ArrayList<>();
            vector.get("ratings").forEach(n -> ratings.add(n.asInt()));
            assertThat(SquadRatingCalculator.squadRating(ratings)).as(vector.get("name").asText()).isEqualTo(vector.get("expected").asInt());
        }
    }
}
