package com.fcareer.catalog;

import com.fcareer.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Kartı olmayan oyuncu: 404 yerine 200 + 1×1 saydam GIF (X-Card-Image: missing). Tarayıcı konsoluna her eksik kart için "Failed to load resource"
 * düşmez; web görselin doğal genişliği 1 px ise kendi kart tasarımını gösterir. Testlerde vekil kapalı (şablon boş) → her kimlik "eksik".
 */
@IntegrationTest
class CardImageEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private com.fcareer.catalog.repository.CardImageRepository repository;

    @Test
    void theDatabaseStoreKeepsFoundAndMissingCards() {
        java.time.Instant at = java.time.Instant.parse("2026-10-18T10:00:00Z");
        byte[] webp = {'R', 'I', 'F', 'F', 9};
        repository.save(910_001L, new com.fcareer.catalog.service.UpstreamImageCache.Stored(new com.fcareer.catalog.service.UpstreamImageCache.Image(webp, "image/webp"), at));
        repository.save(910_002L, new com.fcareer.catalog.service.UpstreamImageCache.Stored(null, at));

        var found = repository.load(910_001L).orElseThrow();
        assertThat(found.image().bytes()).isEqualTo(webp);
        assertThat(found.image().contentType()).isEqualTo("image/webp");
        assertThat(found.fetchedAt()).isEqualTo(at);
        assertThat(repository.load(910_002L).orElseThrow().image()).isNull();
        assertThat(repository.load(910_003L)).isEmpty();
    }

    @Test
    void aMissingCardIsATransparentPixelNotA404() throws Exception {
        var response = mockMvc.perform(get("/api/v1/img/card/203244")).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("image/gif");
        assertThat(response.getHeader("X-Card-Image")).isEqualTo("missing");
        assertThat(response.getContentAsByteArray()).hasSizeLessThan(64).startsWith((byte) 'G', (byte) 'I', (byte) 'F');
        assertThat(response.getHeader("Cache-Control")).contains("max-age=86400");
    }
}
