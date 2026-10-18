package com.fcareer.catalog.service;

import com.fcareer.catalog.configuration.CatalogProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

/**
 * Oyuncu kartı görseli vekili: varsayılan kaynak EA reyting görselleri (`fcareer.catalog.card-image-url-template`, {id} = EA oyuncu kimliği).
 * Tarayıcı yalnız bizim adresimize (/api/v1/img/card/{id}) gider: kullanıcı IP'si EA'ya gitmez, CSP img-src 'self' kalır. Kartı olmayan oyuncu
 * 24 saat hatırlanır; web o zaman kendi kart tasarımını çizer. Şablon boşsa vekil kapalıdır.
 */
@Service
public class CardImageService {

    private static final long MAX_CACHED_BYTES = 128L * 1024 * 1024;
    private static final Duration MISS_TTL = Duration.ofHours(24);

    private final UpstreamImageCache cache;

    @Autowired
    public CardImageService(CatalogProperties properties, RestClient.Builder builder, Clock clock) {
        this.cache = new UpstreamImageCache("Card", properties::getCardImageUrlTemplate, builder, clock, MAX_CACHED_BYTES, MISS_TTL);
    }

    public Optional<UpstreamImageCache.Image> card(long eaId) {
        return cache.get(eaId);
    }
}
