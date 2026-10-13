package com.fcareer.ut.kb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.stream.StreamSupport;

@Component
public class CaptureRules {

    private static final String RESOURCE = "seed/capture-rules.json";

    private final JsonNode rules;
    private final List<String> personalOnly;

    public CaptureRules(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            rules = objectMapper.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        personalOnly = StreamSupport.stream(rules.path("personalOnly").spliterator(), false).map(JsonNode::asText).toList();
    }

    public JsonNode rules() {
        return rules;
    }

    /** Kişisel uç (kulüp, tradepile, watchlist...): katkı hattına hiç girmez. */
    public boolean isPersonalOnly(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return personalOnly.stream().anyMatch(lower::contains);
    }
}
