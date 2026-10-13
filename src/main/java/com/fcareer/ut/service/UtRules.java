package com.fcareer.ut.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/** seed/ut-rules.fc27.json'dan okunan oyun kuralları (BE-ARCH-03): hesaplayıcılar değerleri buradan alır, koda gömülmez. */
@Component
public class UtRules {

    private static final String RESOURCE = "seed/ut-rules.fc27.json";

    private final JsonNode document;

    @Autowired
    public UtRules(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            document = objectMapper.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private UtRules(JsonNode document) {
        this.document = document;
    }

    /** Test ve araçlar için hazır dokümandan. */
    public static UtRules of(JsonNode document) {
        return new UtRules(document);
    }

    /** Rarity id → kart tipi (ICON/HERO/HOF); eşleme rules.rarities.byId'dedir ve katkı capture'ından doldurulur (VERIFY). Bilinmeyen id için null. */
    public String cardTypeOfRarity(Integer rarityId) {
        if (rarityId == null) {
            return null;
        }
        JsonNode type = document.at("/rarities/byId/" + rarityId + "/cardType");
        return type.isMissingNode() || type.isNull() ? null : type.asText();
    }

    public JsonNode document() {
        return document;
    }

    public int squadSize() {
        return document.at("/squadRating/sizes/squad").asInt();
    }

    public int sbcSize() {
        return document.at("/squadRating/sizes/sbc").asInt();
    }

    public JsonNode chemistry() {
        return document.get("chemistry");
    }

    public List<Integer> thresholds(String kind) {
        List<Integer> values = new ArrayList<>();
        chemistry().at("/thresholds/" + kind).forEach(n -> values.add(n.asInt()));
        return values;
    }

    /** Kalite bandı (RULE-03): base kartta rare/common ayrımı yoktur. */
    public String qualityOf(int rating) {
        JsonNode quality = document.get("quality");
        if (rating <= quality.at("/bronze/1").asInt()) {
            return "BRONZE";
        }
        return rating <= quality.at("/silver/1").asInt() ? "SILVER" : "GOLD";
    }

    /** Hat yüksekliğinden Defensive Approach (RULE-06). */
    public String defensiveApproach(int lineHeight) {
        for (JsonNode approach : document.at("/tactics/defensiveApproach")) {
            if (lineHeight >= approach.at("/lineHeight/0").asInt() && lineHeight <= approach.at("/lineHeight/1").asInt()) {
                return approach.get("id").asText();
            }
        }
        throw new IllegalArgumentException("lineHeight " + lineHeight);
    }
}
