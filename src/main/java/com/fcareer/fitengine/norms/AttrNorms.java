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
import java.util.HexFormat;
import java.util.Optional;

/**
 * Attribute normları (E3-3, 13 §3.2 madde 1): her (cinsiyet, ana mevki) için p10/p50/p90/ortalama/sd, `seed/attr-norms.fc27.json`'dan.
 * Fayda u = σ((a − p50)/sd): 0-99 sıkışması kalkar, farklı attribute'lar karşılaştırılabilir olur. Yeterli oyuncusu olmayan grup (kadın LW/RW) erkek normlarına düşer.
 */
@Component
public class AttrNorms {

    private static final String SEED = "seed/attr-norms.fc27.json";
    private static final String HASH = "seed/attr-norms.fc27.hash";
    private static final int MEN = 0;
    /** sd tabanı: 1 attribute puanının faydayı oynatması üst sınırlı kalsın (13 §2 madde 4, uçurum yok). */
    private static final double MIN_SD = 3.0;

    public record Norm(double p10, double p50, double p90, double mean, double sd) {
    }

    private final JsonNode document;
    private final String text;

    @Autowired
    public AttrNorms(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource(SEED).getInputStream()) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            document = objectMapper.readTree(text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Optional<Norm> norm(int gender, String position, Attr attr) {
        JsonNode node = group(gender, position).path("attrs").path(attrKey(attr));
        if (node.isMissingNode() && gender != MEN) {
            node = group(MEN, position).path("attrs").path(attrKey(attr));
        }
        if (node.isMissingNode()) {
            return Optional.empty();
        }
        return Optional.of(new Norm(node.path("p10").asDouble(), node.path("p50").asDouble(), node.path("p90").asDouble(), node.path("mean").asDouble(), node.path("sd").asDouble()));
    }

    /** σ((a − p50)/sd); norm yoksa 0,5. */
    public double utility(int gender, String position, Attr attr, double value) {
        return norm(gender, position, attr).map(n -> 1.0 / (1.0 + Math.exp(-(value - n.p50()) / Math.max(n.sd(), MIN_SD)))).orElse(0.5);
    }

    /** Grubun kendi oyuncu sayısı (fallback'e düşen grup için 0). */
    public int playerCount(int gender, String position) {
        return group(gender, position).path("n").asInt(0);
    }

    public int minPlayers() {
        return document.path("minPlayers").asInt();
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

    private JsonNode group(int gender, String position) {
        return document.path("genders").path(gender == MEN ? "men" : "women").path(position);
    }

    /** Seed anahtarı: "GKDiving" → gkDiving, "FKAccuracy" → freeKickAccuracy, diğerleri ilk harf küçük. */
    private static String attrKey(Attr attr) {
        String key = attr.getKey();
        if ("FKAccuracy".equals(key)) {
            return "freeKickAccuracy";
        }
        if (key.startsWith("GK")) {
            return "gk" + key.substring(2);
        }
        return Character.toLowerCase(key.charAt(0)) + key.substring(1);
    }
}
