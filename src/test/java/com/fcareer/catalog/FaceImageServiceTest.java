package com.fcareer.catalog;

import com.fcareer.catalog.configuration.CatalogProperties;
import com.fcareer.catalog.service.FaceImageService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class FaceImageServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};

    private HttpServer server;
    private final AtomicInteger hits = new AtomicInteger();
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-13T10:00:00Z"));
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
        server.createContext("/p", exchange -> {
            hits.incrementAndGet();
            String path = exchange.getRequestURI().getPath();
            boolean known = path.endsWith("p42.png") || path.endsWith("p43.png") || path.endsWith("p44.png");
            if (known) {
                exchange.getResponseHeaders().add("Content-Type", "image/webp");
            }
            exchange.sendResponseHeaders(known ? 200 : 404, known ? PNG.length : -1);
            if (known) {
                exchange.getResponseBody().write(PNG);
            }
            exchange.close();
        });
        server.start();
        properties = new CatalogProperties();
        properties.setFaceUrlTemplate("http://127.0.0.1:" + server.getAddress().getPort() + "/p{id}.png");
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private FaceImageService service(long maxBytes) {
        return new FaceImageService(properties, RestClient.builder(), clock, maxBytes);
    }

    @Test
    void servesFromUpstreamOnceThenFromMemoryWithTheUpstreamContentType() {
        FaceImageService service = service(1024);

        assertThat(service.face(42)).hasValueSatisfying(image -> {
            assertThat(image.bytes()).isEqualTo(PNG);
            assertThat(image.contentType()).isEqualTo("image/webp");
        });
        assertThat(service.face(42)).isPresent();
        assertThat(hits.get()).isEqualTo(1);
    }

    @Test
    void missesAreRememberedForAnHourThenRetried() {
        FaceImageService service = service(1024);

        assertThat(service.face(7)).isEmpty();
        assertThat(service.face(7)).isEmpty();
        assertThat(hits.get()).as("ikinci istek EA'ya gitmez").isEqualTo(1);

        now.set(now.get().plus(Duration.ofMinutes(61)));
        assertThat(service.face(7)).isEmpty();
        assertThat(hits.get()).isEqualTo(2);
    }

    @Test
    void memoryIsBoundedByTotalBytesNotEntryCount() {
        FaceImageService service = service(PNG.length * 2L);

        service.face(42);
        service.face(43);
        service.face(44);
        assertThat(hits.get()).isEqualTo(3);

        service.face(44);
        service.face(43);
        assertThat(hits.get()).as("son iki görsel bellekte").isEqualTo(3);
        service.face(42);
        assertThat(hits.get()).as("en eski görsel bayt sınırı yüzünden atıldı").isEqualTo(4);
    }

    @Test
    void invalidIdsAreEmptyWithoutAnUpstreamCall() {
        FaceImageService service = service(1024);

        assertThat(service.face(0)).isEmpty();
        assertThat(service.face(-5)).isEmpty();
        assertThat(hits.get()).isZero();
    }

    @Test
    void emptyTemplateDisablesEverything() {
        properties.setFaceUrlTemplate("");
        FaceImageService service = service(1024);

        assertThat(service.enabled()).isFalse();
        assertThat(service.face(42)).isEmpty();
        assertThat(hits.get()).isZero();
    }
}
