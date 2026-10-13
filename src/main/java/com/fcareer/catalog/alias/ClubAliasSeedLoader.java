package com.fcareer.catalog.alias;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.catalog.entity.ClubEntity;
import com.fcareer.catalog.entity.LeagueEntity;
import com.fcareer.catalog.repository.ClubRepository;
import com.fcareer.catalog.repository.LeagueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

/**
 * seed/club-aliases.json → club_alias (hash'li, idempotent). Kayıtlar doğal anahtarla yazılır (EA team id ya da ad+lig);
 * kanonik club bulunamazsa o kayıt atlanır ve loglanır (başka ortamda aynı id'ler olmayabilir).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClubAliasSeedLoader implements ApplicationRunner {

    static final String SEED = "seed/club-aliases.json";
    static final String HASH = "seed/club-aliases.hash";

    private final ObjectMapper objectMapper;
    private final ClubRepository clubRepository;
    private final LeagueRepository leagueRepository;
    private final ClubAliasRepository aliases;

    /** Seed dosyasının sha256'sı (satır sonları LF'ye normalize). */
    public static String seedHash() throws IOException {
        try (InputStream in = new ClassPathResource(SEED).getInputStream()) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        String expected;
        try (InputStream in = new ClassPathResource(HASH).getInputStream()) {
            expected = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
        if (!expected.equals(seedHash())) {
            throw new IllegalStateException("club-aliases.json hash'i " + HASH + " ile uyuşmuyor");
        }
        int written = 0;
        try (InputStream in = new ClassPathResource(SEED).getInputStream()) {
            for (JsonNode node : objectMapper.readTree(in).path("aliases")) {
                if (write(node)) {
                    written++;
                }
            }
        }
        log.info("Club alias seed loaded written : {}", written);
    }

    private boolean write(JsonNode node) {
        Optional<ClubEntity> canonical = canonical(node.path("canonical"));
        if (canonical.isEmpty()) {
            log.warn("Club alias skipped alias : {} reason : canonical club not found", node.path("aliasName").asText());
            return false;
        }
        Integer leagueId = node.path("league").isTextual() ? leagueRepository.findByName(node.path("league").asText()).map(LeagueEntity::getId).orElse(null) : null;
        Integer gender = node.path("gender").isNumber() ? node.path("gender").asInt() : null;
        aliases.upsert(node.path("aliasName").asText(), leagueId, gender, canonical.get().getEaTeamId(), canonical.get().getId(), node.path("source").asText("seed"), node.path("verified").asBoolean(false));
        return true;
    }

    private Optional<ClubEntity> canonical(JsonNode key) {
        if (key.path("eaTeamId").isInt()) {
            return clubRepository.findFirstByEaTeamIdOrderByIdAsc(key.path("eaTeamId").asInt());
        }
        Integer leagueId = leagueRepository.findByName(key.path("league").asText()).map(LeagueEntity::getId).orElse(null);
        return Objects.isNull(leagueId) ? Optional.empty() : clubRepository.findByNameAndLeagueId(key.path("name").asText(), leagueId);
    }
}
