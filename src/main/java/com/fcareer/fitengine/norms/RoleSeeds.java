package com.fcareer.fitengine.norms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.fitengine.model.Attr;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Rol seed'leri (E3-4, 13 §3.2 madde 2-7): rol vektörleri v3, temel attribute'lar (z-skoru), PlayStyle ağırlıkları, aşinalık/ayak/fiziksel katsayıları.
 * Hepsi `tools/fit-seeds/gen-role-seeds.mjs` çıktısıdır (verified:false); motor (E3-5) değerleri buradan okur, koda gömmez.
 */
@Component
public class RoleSeeds {

    public static final List<String> FILES = List.of("role-vectors", "role-requirements", "role-playstyles", "role-modifiers");

    public record BaseAttr(Attr attr, double minZ, double targetZ, double tauZ) {
    }

    public record RolePlaystyle(String playstyle, int weight) {
    }

    private final Map<String, JsonNode> documents = new HashMap<>();
    private final Map<String, String> texts = new HashMap<>();

    @Autowired
    public RoleSeeds(ObjectMapper objectMapper) {
        for (String name : FILES) {
            try (InputStream in = new ClassPathResource("seed/" + name + ".fc27.json").getInputStream()) {
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                texts.put(name, text);
                documents.put(name, objectMapper.readTree(text));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /** (pozisyon, rol, focus) ağırlık vektörü, toplam 1; yoksa boş. */
    public Optional<Map<Attr, Double>> vector(String position, String roleId, String focus) {
        JsonNode node = documents.get("role-vectors").path("vectors").path(position + "|" + roleId + "|" + focus);
        if (node.isMissingNode()) {
            return Optional.empty();
        }
        return Optional.of(toMap(node));
    }

    public List<String> vectorKeys() {
        List<String> keys = new ArrayList<>();
        documents.get("role-vectors").path("vectors").fieldNames().forEachRemaining(keys::add);
        return keys;
    }

    /** Vektördeki EA OVR regresyonu kütle payı (CI: ≤ 0,5). */
    public double ovrMass(String key) {
        return documents.get("role-vectors").path("ovrMass").path(key).asDouble(0);
    }

    public List<BaseAttr> baseAttrs(String roleId) {
        List<BaseAttr> out = new ArrayList<>();
        for (JsonNode n : documents.get("role-requirements").path("requirements").path(roleId)) {
            Attr.fromKey(n.path("attr").asText()).ifPresent(a -> out.add(new BaseAttr(a, n.path("minZ").asDouble(), n.path("targetZ").asDouble(), n.path("tauZ").asDouble())));
        }
        return out;
    }

    public List<RolePlaystyle> playstyles(String roleId) {
        List<RolePlaystyle> out = new ArrayList<>();
        for (JsonNode n : documents.get("role-playstyles").path("playstyles").path(roleId)) {
            out.add(new RolePlaystyle(n.path("ps").asText(), n.path("weight").asInt()));
        }
        return out;
    }

    public JsonNode modifiers() {
        return documents.get("role-modifiers");
    }

    public JsonNode document(String name) {
        return documents.get(name);
    }

    public String hash(String name) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texts.get(name).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public String expectedHash(String name) {
        try (InputStream in = new ClassPathResource("seed/" + name + ".fc27.hash").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map<Attr, Double> toMap(JsonNode node) {
        Map<Attr, Double> map = new EnumMap<>(Attr.class);
        Iterator<Map.Entry<String, JsonNode>> it = node.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            Attr.fromKey(e.getKey()).ifPresent(a -> map.put(a, e.getValue().asDouble()));
        }
        return map;
    }
}
