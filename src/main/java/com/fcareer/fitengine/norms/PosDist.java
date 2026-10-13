package com.fcareer.fitengine.norms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Mevki uzaklığı çarpanı (E3-3, 13 §3.2 madde 8): posDist[doğal][hedef], `seed/pos-dist.fc27.json` (verified:false). Bilinmeyen mevki çifti 0. */
@Component
public class PosDist {

    private static final String SEED = "seed/pos-dist.fc27.json";
    private static final String HASH = "seed/pos-dist.fc27.hash";

    private final JsonNode document;
    private final String text;

    @Autowired
    public PosDist(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource(SEED).getInputStream()) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            document = objectMapper.readTree(text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public double factor(String natural, String target) {
        return document.path("matrix").path(natural).path(target).asDouble(0.0);
    }

    public boolean verified() {
        return document.path("verified").asBoolean(false);
    }

    public String hash() {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public String expectedHash() {
        try (InputStream in = new ClassPathResource(HASH).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
