package com.fcareer.catalog;

import com.fcareer.catalog.configuration.CatalogProperties;
import com.fcareer.catalog.service.FaceImageService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class FaceImageServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};

    private HttpServer server;
    private final AtomicInteger hits = new AtomicInteger();
    private CatalogProperties properties;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/p", exchange -> {
            hits.incrementAndGet();
            boolean known = exchange.getRequestURI().getPath().endsWith("p42.png");
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

    @Test
    void servesFromUpstreamOnceThenFromMemory() {
        FaceImageService service = new FaceImageService(properties, RestClient.builder());

        assertThat(service.face(42)).contains(PNG);
        assertThat(service.face(42)).contains(PNG);
        assertThat(hits.get()).isEqualTo(1);
    }

    @Test
    void unknownIdAndUpstreamErrorsAreEmpty() {
        FaceImageService service = new FaceImageService(properties, RestClient.builder());

        assertThat(service.face(7)).isEmpty();
        assertThat(service.face(0)).isEmpty();
    }

    @Test
    void emptyTemplateDisablesEverything() {
        properties.setFaceUrlTemplate("");
        FaceImageService service = new FaceImageService(properties, RestClient.builder());

        assertThat(service.enabled()).isFalse();
        assertThat(service.face(42)).isEmpty();
        assertThat(hits.get()).isZero();
    }
}
