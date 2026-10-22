package com.fcareer.tactics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KARIYER-UX FAZ 1: varsayılan takım kurulumu tek kaynaktan. Backend sabiti = seed/tactic-defaults.json; web kopyası
 * (src/lib/tacticDefaults.json, DEFAULT_SETUP) birebir aynı. Kardeş web reposu diskteyse iki kopya da karşılaştırılır (yerelde; CI'da her repo kendi tarafını).
 */
class TacticDefaultsParityTest {

    private static final Path WEB_COPY = Path.of("..", "FCarrierTacticBuilderWeb", "src", "lib", "tacticDefaults.json");

    @Test
    void theConstantMatchesTheSeedAndTheWebCopy() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode seed = mapper.readTree(new ClassPathResource("seed/tactic-defaults.json").getInputStream());

        assertThat(seed.at("/setup/buildUp").asText()).isEqualTo(TacticDefaults.BUILD_UP);
        assertThat(seed.at("/setup/depth").asInt()).isEqualTo(TacticDefaults.DEPTH);
        assertThat(TacticDefaults.setup().getBuildUp()).isEqualTo(TacticDefaults.BUILD_UP);
        assertThat(TacticDefaults.setup()).as("her çağrıda yeni kopya").isNotSameAs(TacticDefaults.setup());
        if (Files.exists(WEB_COPY)) {
            assertThat(mapper.readTree(WEB_COPY.toFile())).as("web kopyası backend seed'iyle aynı").isEqualTo(seed);
        }
    }
}
