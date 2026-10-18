package com.fcareer.catalog;

import com.fcareer.catalog.configuration.CatalogProperties;
import com.fcareer.catalog.service.CardImageService;
import com.fcareer.catalog.service.UpstreamImageCache;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EA kart görseli vekili: tarayıcı yalnız bizim adresimizi görür (kullanıcı IP'si EA'ya gitmez, CSP img-src 'self' kalır); sunucu görseli
 * yapılandırılan şablondan (varsayılan EA reyting görselleri, oyuncu kimliği {id}) alır ve önbelleğe koyar. Yoksa 404 → web kendi kartını çizer.
 * Test yerel bir HTTP sunucusuna karşı koşar; EA'ya istek yok.
 */
class CardImageServiceTest {

    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 1, 2, 3, 'W', 'E', 'B', 'P'};

    private HttpServer server;
    private final AtomicInteger hits = new AtomicInteger();
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-18T10:00:00Z"));
    private CatalogProperties properties;

    private final Clock clock = new Clock() {
        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/FC27", exchange -> {
            hits.incrementAndGet();
            String path = exchange.getRequestURI().getPath();
            lastPath.set(path);
            boolean known = path.endsWith("/231747_tr-TR.webp");
            if (known) {
                exchange.getResponseHeaders().add("Content-Type", "image/webp");
            }
            exchange.sendResponseHeaders(known ? 200 : 404, known ? WEBP.length : -1);
            if (known) {
                exchange.getResponseBody().write(WEBP);
            }
            exchange.close();
        });
        server.start();
        properties = new CatalogProperties();
        properties.setCardImageUrlTemplate("http://127.0.0.1:" + server.getAddress().getPort() + "/FC27/components/items/{id}_tr-TR.webp");
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private CardImageService service() {
        return new CardImageService(properties, RestClient.builder(), clock);
    }

    @Test
    void theDefaultTemplateIsTheEaRatingsCardImage() {
        assertThat(new CatalogProperties().getCardImageUrlTemplate()).isEqualTo("https://ratings-images-prod.pulse.ea.com/FC27/components/items/{id}_tr-TR.webp");
    }

    @Test
    void aKnownCardIsFetchedOnceThenServedFromMemory() {
        CardImageService service = service();

        var first = service.card(231747);
        var second = service.card(231747);

        assertThat(first).isPresent();
        assertThat(first.get().contentType()).isEqualTo("image/webp");
        assertThat(first.get().bytes()).isEqualTo(WEBP);
        assertThat(second).isPresent();
        assertThat(hits.get()).isEqualTo(1);
        assertThat(lastPath.get()).isEqualTo("/FC27/components/items/231747_tr-TR.webp");
    }

    @Test
    void aMissingCardIsRememberedSoTheUpstreamIsNotHammered() {
        CardImageService service = service();

        assertThat(service.card(203244)).isEmpty();
        assertThat(service.card(203244)).isEmpty();
        assertThat(hits.get()).isEqualTo(1);

        now.set(now.get().plusSeconds(8 * 24 * 3600));
        assertThat(service.card(203244)).isEmpty();
        assertThat(hits.get()).as("bulunamayan 7 gün sonra yeniden denenir").isEqualTo(2);
    }

    /** Kalıcı depo (DB): kartlar değişmez; yeniden başlatmadan sonra var olan görsel için kaynağa gidilmez, eksik olan 7 gün hatırlanır. */
    @Test
    void storedCardsSurviveARestartAndMissingOnesAreRecheckedAfterAWeek() {
        java.util.Map<Long, UpstreamImageCache.Stored> db = new java.util.HashMap<>();
        UpstreamImageCache.Store store = new UpstreamImageCache.Store() {
            @Override
            public java.util.Optional<UpstreamImageCache.Stored> load(long id) {
                return java.util.Optional.ofNullable(db.get(id));
            }

            @Override
            public void save(long id, UpstreamImageCache.Stored stored) {
                db.put(id, stored);
            }
        };
        new CardImageService(properties, RestClient.builder(), clock, store).card(231747);
        new CardImageService(properties, RestClient.builder(), clock, store).card(203244);
        assertThat(hits.get()).isEqualTo(2);

        CardImageService restarted = new CardImageService(properties, RestClient.builder(), clock, store);
        assertThat(restarted.card(231747)).isPresent();
        assertThat(restarted.card(203244)).isEmpty();
        assertThat(hits.get()).as("yeniden başlatma sonrası kaynağa gidilmez").isEqualTo(2);

        now.set(now.get().plusSeconds(8 * 24 * 3600));
        assertThat(new CardImageService(properties, RestClient.builder(), clock, store).card(203244)).isEmpty();
        assertThat(hits.get()).as("eksik kart 7 gün sonra yeniden denenir").isEqualTo(3);
        assertThat(new CardImageService(properties, RestClient.builder(), clock, store).card(231747)).isPresent();
        assertThat(hits.get()).as("var olan kart hiç yeniden indirilmez").isEqualTo(3);
    }

    @Test
    void aBlankTemplateDisablesTheProxyWithoutAnyRequest() {
        properties.setCardImageUrlTemplate("");
        assertThat(service().card(231747)).isEmpty();
        assertThat(service().card(-1)).isEmpty();
        assertThat(hits.get()).isZero();
    }
}
