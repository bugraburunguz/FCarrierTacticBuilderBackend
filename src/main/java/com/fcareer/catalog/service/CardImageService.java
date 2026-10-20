package com.fcareer.catalog.service;

import com.fcareer.catalog.configuration.CatalogProperties;
import com.fcareer.catalog.repository.CardImageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

/**
 * Oyuncu kartı görseli vekili: varsayılan kaynak EA reyting görselleri (`fcareer.catalog.card-image-url-template`, {id} = EA oyuncu kimliği).
 * Tarayıcı yalnız bizim adresimize (/api/v1/img/card/{id}) gider: kullanıcı IP'si EA'ya gitmez, CSP img-src 'self' kalır.
 * Önbellek: bellek (32 MB, sıcak kartlar; OPS-03: kalıcı kopya DB'de olduğu için büyük bellek gereksiz) → DB (card_image, kalıcı; kartlar
 * değişmez) → kaynak. Kartı olmayan oyuncu 7 gün hatırlanır; web o zaman kendi kart
 * tasarımını çizer. Şablon boşsa vekil kapalıdır.
 */
@Service
public class CardImageService {

    private static final long MAX_CACHED_BYTES = 32L * 1024 * 1024;
    private static final Duration MISS_TTL = Duration.ofDays(7);

    private final UpstreamImageCache cache;

    @Autowired
    public CardImageService(CatalogProperties properties, RestClient.Builder builder, Clock clock, CardImageRepository repository) {
        this(properties, builder, clock, (UpstreamImageCache.Store) repository);
    }

    public CardImageService(CatalogProperties properties, RestClient.Builder builder, Clock clock) {
        this(properties, builder, clock, (UpstreamImageCache.Store) null);
    }

    public CardImageService(CatalogProperties properties, RestClient.Builder builder, Clock clock, UpstreamImageCache.Store store) {
        this.cache = new UpstreamImageCache("Card", properties::getCardImageUrlTemplate, builder, clock, MAX_CACHED_BYTES, MISS_TTL, store);
    }

    public Optional<UpstreamImageCache.Image> card(long eaId) {
        return cache.get(eaId);
    }
}
