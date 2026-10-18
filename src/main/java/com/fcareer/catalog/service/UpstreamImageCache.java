package com.fcareer.catalog.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.ResponseEntity;
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
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * Dış görsel vekilinin ortak kısmı (yüz ve kart): adres yalnız yapılandırılan şablondan ({id} sayısal kimlik) kurulur, şablon boşsa hiç istek
 * atılmaz (yayından kaldırma anahtarı). Bellek toplam baytla sınırlı (LRU); bulunamayan kimlik belirli süre hatırlanır; kaynağa eşzamanlı istek
 * sınırlıdır (bir sayfa onlarca kart ister).
 */
@Slf4j
public final class UpstreamImageCache {

    public record Image(byte[] bytes, String contentType) {
    }

    /** Kalıcı depodaki kayıt: image null ise kaynakta yok (fetchedAt'ten itibaren missTtl boyunca yeniden denenmez). */
    public record Stored(Image image, Instant fetchedAt) {
    }

    /** İsteğe bağlı kalıcı depo (ör. DB): yeniden başlatmada var olan görsel için kaynağa gidilmez. */
    public interface Store {
        Optional<Stored> load(long id);

        void save(long id, Stored stored);
    }

    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int MAX_MISSES = 20_000;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_CONCURRENT = 8;
    private static final String DEFAULT_TYPE = "image/png";

    private final String label;
    private final Supplier<String> template;
    private final RestClient client;
    private final Clock clock;
    private final long maxCachedBytes;
    private final Duration missTtl;
    private final Store store;
    private final Semaphore upstream = new Semaphore(MAX_CONCURRENT);
    private final Map<Long, Image> cache = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<Long, Instant> misses = new LinkedHashMap<>();
    private long cachedBytes;

    public UpstreamImageCache(String label, Supplier<String> template, RestClient.Builder builder, Clock clock, long maxCachedBytes, Duration missTtl) {
        this(label, template, builder, clock, maxCachedBytes, missTtl, null);
    }

    public UpstreamImageCache(String label, Supplier<String> template, RestClient.Builder builder, Clock clock, long maxCachedBytes, Duration missTtl,
                              Store store) {
        this.store = store;
        this.label = label;
        this.template = template;
        this.clock = clock;
        this.maxCachedBytes = maxCachedBytes;
        this.missTtl = missTtl;
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS.withConnectTimeout(TIMEOUT).withReadTimeout(TIMEOUT);
        this.client = builder.requestFactory(ClientHttpRequestFactories.get(settings)).build();
    }

    public boolean enabled() {
        String value = template.get();
        return Objects.nonNull(value) && !value.isBlank();
    }

    public Optional<Image> get(long id) {
        if (!enabled() || id <= 0) {
            return Optional.empty();
        }
        synchronized (this) {
            Image hit = cache.get(id);
            if (Objects.nonNull(hit)) {
                return Optional.of(hit);
            }
            Instant missedAt = misses.get(id);
            if (Objects.nonNull(missedAt) && missedAt.plus(missTtl).isAfter(clock.instant())) {
                return Optional.empty();
            }
        }
        Optional<Stored> stored = loadStored(id);
        if (stored.isPresent()) {
            Stored hit = stored.get();
            if (Objects.nonNull(hit.image())) {
                synchronized (this) {
                    remember(id, hit.image());
                }
                return Optional.of(hit.image());
            }
            if (hit.fetchedAt().plus(missTtl).isAfter(clock.instant())) {
                synchronized (this) {
                    forget(id, hit.fetchedAt());
                }
                return Optional.empty();
            }
        }
        Optional<Image> fetched = fetch(id);
        synchronized (this) {
            if (fetched.isPresent()) {
                remember(id, fetched.get());
            } else {
                forget(id, clock.instant());
            }
        }
        saveStored(id, new Stored(fetched.orElse(null), clock.instant()));
        return fetched;
    }

    private Optional<Stored> loadStored(long id) {
        if (Objects.isNull(store)) {
            return Optional.empty();
        }
        try {
            return store.load(id);
        } catch (RuntimeException e) {
            log.warn("{} image store read failed id : {} reason : {}", label, id, e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private void saveStored(long id, Stored stored) {
        if (Objects.isNull(store)) {
            return;
        }
        try {
            store.save(id, stored);
        } catch (RuntimeException e) {
            log.warn("{} image store write failed id : {} reason : {}", label, id, e.getClass().getSimpleName());
        }
    }

    private Optional<Image> fetch(long id) {
        try {
            upstream.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
        try {
            ResponseEntity<byte[]> response = client.get().uri(template.get().replace("{id}", String.valueOf(id))).retrieve().toEntity(byte[].class);
            byte[] body = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && Objects.nonNull(body) && body.length > 0 && body.length <= MAX_BYTES) {
                String type = Objects.nonNull(response.getHeaders().getContentType()) ? response.getHeaders().getContentType().toString() : DEFAULT_TYPE;
                return Optional.of(new Image(body, type));
            }
        } catch (RestClientException e) {
            log.debug("{} image upstream miss id : {} reason : {}", label, id, e.getClass().getSimpleName());
        } finally {
            upstream.release();
        }
        return Optional.empty();
    }

    private void remember(long id, Image image) {
        misses.remove(id);
        Image previous = cache.put(id, image);
        cachedBytes += image.bytes().length - (Objects.nonNull(previous) ? previous.bytes().length : 0);
        Iterator<Map.Entry<Long, Image>> eldest = cache.entrySet().iterator();
        while (cachedBytes > maxCachedBytes && eldest.hasNext()) {
            cachedBytes -= eldest.next().getValue().bytes().length;
            eldest.remove();
        }
    }

    private void forget(long id, Instant at) {
        misses.put(id, at);
        if (misses.size() > MAX_MISSES) {
            Iterator<Long> eldest = misses.keySet().iterator();
            eldest.next();
            eldest.remove();
        }
    }
}
