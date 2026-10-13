package com.fcareer.catalog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.Map;

/** Lig seviyesi (BE-BUG-17): EA lig id'sine göre `seed/league-tiers.json` değerleri `league.tier`'a yazılır; ML `league_tier` buradan beslenir. */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeagueTierService {

    private static final String SEED = "seed/league-tiers.json";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    /** Seed'deki her EA lig id'si için tier'ı yazar; EA id'si henüz eşlenmemiş ligler atlanır. Etkilenen lig sayısını döner. */
    public int apply() {
        int updated = 0;
        Iterator<Map.Entry<String, JsonNode>> tiers = read().path("tiers").fields();
        while (tiers.hasNext()) {
            Map.Entry<String, JsonNode> entry = tiers.next();
            updated += jdbc.update("UPDATE league SET tier = ? WHERE ea_league_id = ? AND tier IS DISTINCT FROM ?",
                    (short) entry.getValue().asInt(), Integer.valueOf(entry.getKey()), (short) entry.getValue().asInt());
        }
        if (updated > 0) {
            log.info("League tiers applied leagues : {}", updated);
        }
        return updated;
    }

    private JsonNode read() {
        try (InputStream in = new ClassPathResource(SEED).getInputStream()) {
            return objectMapper.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
