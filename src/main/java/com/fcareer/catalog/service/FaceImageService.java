package com.fcareer.catalog.service;

import com.fcareer.catalog.configuration.CatalogProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Oyuncu portresi proxy'si (08 D-04): görsel kendi alan adımızdan servis edilir, kaynağa gitmeden önce bellekte tutulur.
 * Şablon boşsa hiçbir görsel dönmez (takedown anahtarı); kimlik yalnız sayısaldır, adres yalnız yapılandırılan şablondan kurulur.
 * Bellek girdi sayısıyla değil toplam bayt ile sınırlıdır; bulunamayan kimlik bir saat hatırlanır (BE-SEC-11).
 */
@Slf4j
@Service
public class FaceImageService {

    public record FaceImage(byte[] bytes, String contentType) {
    }

    private static final long MAX_CACHED_BYTES = 64L * 1024 * 1024;
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int MAX_MISSES = 10_000;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final Duration MISS_TTL = Duration.ofHours(1);
    private static final String DEFAULT_TYPE = "image/png";

    private final CatalogProperties properties;
    private final RestClient client;
    private final Clock clock;
    private final long maxCachedBytes;
    private final Map<Long, FaceImage> cache = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<Long, Instant> misses = new LinkedHashMap<>();
    private long cachedBytes;

    @Autowired
    public FaceImageService(CatalogProperties properties, RestClient.Builder builder, Clock clock) {
        this(properties, builder, clock, MAX_CACHED_BYTES);
    }

    public FaceImageService(CatalogProperties properties, RestClient.Builder builder, Clock clock, long maxCachedBytes) {
        this.properties = properties;
        this.clock = clock;
        this.maxCachedBytes = maxCachedBytes;
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS.withConnectTimeout(TIMEOUT).withReadTimeout(TIMEOUT);
        this.client = builder.requestFactory(ClientHttpRequestFactories.get(settings)).build();
    }

    public boolean enabled() {
        String template = properties.getFaceUrlTemplate();
        return Objects.nonNull(template) && !template.isBlank();
    }

    public Optional<FaceImage> face(long id) {
        if (!enabled() || id <= 0) {
            return Optional.empty();
        }
        synchronized (this) {
            FaceImage hit = cache.get(id);
            if (Objects.nonNull(hit)) {
                return Optional.of(hit);
            }
            Instant missedAt = misses.get(id);
            if (Objects.nonNull(missedAt) && missedAt.plus(MISS_TTL).isAfter(clock.instant())) {
                return Optional.empty();
            }
        }
        Optional<FaceImage> fetched = fetch(id);
        synchronized (this) {
            if (fetched.isPresent()) {
                remember(id, fetched.get());
            } else {
                forget(id);
            }
        }
        return fetched;
    }

    private Optional<FaceImage> fetch(long id) {
        try {
            ResponseEntity<byte[]> response = client.get().uri(properties.getFaceUrlTemplate().replace("{id}", String.valueOf(id))).retrieve().toEntity(byte[].class);
            byte[] body = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && Objects.nonNull(body) && body.length > 0 && body.length <= MAX_BYTES) {
                String type = Objects.nonNull(response.getHeaders().getContentType()) ? response.getHeaders().getContentType().toString() : DEFAULT_TYPE;
                return Optional.of(new FaceImage(body, type));
            }
        } catch (RestClientException e) {
            log.warn("Face image upstream failed id : {} reason : {}", id, e.getMessage());
        }
        return Optional.empty();
    }

    private void remember(long id, FaceImage image) {
        misses.remove(id);
        FaceImage previous = cache.put(id, image);
        cachedBytes += image.bytes().length - (Objects.nonNull(previous) ? previous.bytes().length : 0);
        Iterator<Map.Entry<Long, FaceImage>> eldest = cache.entrySet().iterator();
        while (cachedBytes > maxCachedBytes && eldest.hasNext()) {
            cachedBytes -= eldest.next().getValue().bytes().length;
            eldest.remove();
        }
    }

    private void forget(long id) {
        misses.put(id, clock.instant());
        if (misses.size() > MAX_MISSES) {
            Iterator<Long> eldest = misses.keySet().iterator();
            eldest.next();
            eldest.remove();
        }
    }
}
