package com.fcareer.gameconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.fitengine.model.Attr;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

/** EA oyun config'inden (extension yakalaması) gelen, kariyer ve UT'nin ortak kullandığı referans veriler. */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameConfigService {

    private static final String KEY_ATTRIBUTES = "seed/ea-key-attributes.json";

    private final ObjectMapper objectMapper;

    private KeyAttributes keyAttributes;

    public record KeyAttributes(String source, Map<String, List<String>> positions) {
    }

    @PostConstruct
    void load() {
        try (InputStream in = new ClassPathResource(KEY_ATTRIBUTES).getInputStream()) {
            keyAttributes = objectMapper.readValue(in, KeyAttributes.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        keyAttributes.positions().forEach((position, attrs) -> attrs.forEach(Attr::requireKey));
        log.info("EA key attributes loaded positions : {}", keyAttributes.positions().size());
    }

    public KeyAttributes keyAttributes() {
        return keyAttributes;
    }
}
