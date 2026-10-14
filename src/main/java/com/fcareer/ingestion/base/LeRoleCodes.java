package com.fcareer.ingestion.base;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** LE rol kodu (1-52; career_player.role_familiarity anahtarı) → motor rol kimliği (roles.json). Eşleme ad normalleştirmesiyle (seed/le-role-codes.json). */
@Component
public class LeRoleCodes {

    private final Map<String, String> roleIdByCode = new HashMap<>();

    @Autowired
    public LeRoleCodes(ObjectMapper objectMapper) {
        Map<String, String> idByName = new HashMap<>();
        Map<String, JsonNode> codes = new HashMap<>();
        try (InputStream roles = new ClassPathResource("seed/roles.json").getInputStream(); InputStream seed = new ClassPathResource("seed/le-role-codes.json").getInputStream()) {
            objectMapper.readTree(roles).forEach((r) -> idByName.put(normalize(r.path("name").asText()), r.path("id").asText()));
            objectMapper.readTree(seed).path("codes").fields().forEachRemaining((e) -> codes.put(e.getKey(), e.getValue()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        codes.forEach((code, def) -> {
            String id = idByName.get(normalize(def.path("role").asText()));
            if (Objects.nonNull(id)) {
                roleIdByCode.put(code, id);
            }
        });
    }

    /** {"41": 2, "42": 1} → {"advanced_forward": 2, "poacher": 1}; aynı role birden çok mevkiden gelirse yüksek seviye kalır. Tanınmayan kodlar atlanır. */
    public Map<String, Integer> toRoleIds(Map<String, Integer> familiarityByCode) {
        Map<String, Integer> out = new HashMap<>();
        familiarityByCode.forEach((code, level) -> {
            String id = roleIdByCode.get(code);
            if (Objects.nonNull(id)) {
                out.merge(id, level, Math::max);
            }
        });
        return out;
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
