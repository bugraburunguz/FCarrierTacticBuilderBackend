package com.fcareer.catalog.service;

import com.fcareer.catalog.configuration.CatalogProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Oyuncu portresi proxy'si (08 D-04): görsel kendi alan adımızdan servis edilir, kaynağa gitmeden önce bellekte tutulur.
 * Şablon boşsa hiçbir görsel dönmez (takedown anahtarı); kimlik yalnız sayısaldır, adres yalnız yapılandırılan şablondan kurulur.
 */
@Slf4j
@Service
public class FaceImageService {

    private static final int MAX_CACHED = 400;
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final CatalogProperties properties;
    private final RestClient client;
    private final Map<Long, byte[]> cache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, byte[]> eldest) {
            return size() > MAX_CACHED;
        }
    });

    public FaceImageService(CatalogProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS.withConnectTimeout(TIMEOUT).withReadTimeout(TIMEOUT);
        this.client = builder.requestFactory(ClientHttpRequestFactories.get(settings)).build();
    }

    public boolean enabled() {
        String template = properties.getFaceUrlTemplate();
        return Objects.nonNull(template) && !template.isBlank();
    }

    public Optional<byte[]> face(long id) {
        if (!enabled() || id <= 0) {
            return Optional.empty();
        }
        byte[] hit = cache.get(id);
        if (Objects.nonNull(hit)) {
            return Optional.of(hit);
        }
        try {
            ResponseEntity<byte[]> response = client.get().uri(properties.getFaceUrlTemplate().replace("{id}", String.valueOf(id))).retrieve().toEntity(byte[].class);
            byte[] body = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && Objects.nonNull(body) && body.length > 0 && body.length <= MAX_BYTES) {
                cache.put(id, body);
                return Optional.of(body);
            }
        } catch (RestClientException e) {
            log.warn("Face image upstream failed id : {} reason : {}", id, e.getMessage());
        }
        return Optional.empty();
    }
}
