package com.fcareer.catalog.service;

import com.fcareer.catalog.configuration.CatalogProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

/**
 * Oyuncu portresi proxy'si (08 D-04): görsel kendi alan adımızdan servis edilir, kaynağa gitmeden önce bellekte tutulur.
 * Şablon boşsa hiçbir görsel dönmez (takedown anahtarı); kimlik yalnız sayısaldır, adres yalnız yapılandırılan şablondan kurulur.
 * Bellek girdi sayısıyla değil toplam bayt ile sınırlıdır; bulunamayan kimlik bir saat hatırlanır (BE-SEC-11). Ortak kısım UpstreamImageCache.
 */
@Service
public class FaceImageService {

    public record FaceImage(byte[] bytes, String contentType) {
    }

    private static final long MAX_CACHED_BYTES = 64L * 1024 * 1024;
    private static final Duration MISS_TTL = Duration.ofHours(1);

    private final UpstreamImageCache cache;

    @Autowired
    public FaceImageService(CatalogProperties properties, RestClient.Builder builder, Clock clock) {
        this(properties, builder, clock, MAX_CACHED_BYTES);
    }

    public FaceImageService(CatalogProperties properties, RestClient.Builder builder, Clock clock, long maxCachedBytes) {
        this.cache = new UpstreamImageCache("Face", properties::getFaceUrlTemplate, builder, clock, maxCachedBytes, MISS_TTL);
    }

    public boolean enabled() {
        return cache.enabled();
    }

    public Optional<FaceImage> face(long id) {
        return cache.get(id).map(image -> new FaceImage(image.bytes(), image.contentType()));
    }
}
