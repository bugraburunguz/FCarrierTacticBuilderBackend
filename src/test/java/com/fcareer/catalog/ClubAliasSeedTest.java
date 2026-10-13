package com.fcareer.catalog;

import com.fcareer.IntegrationTest;
import com.fcareer.catalog.alias.ClubAliasRepository;
import com.fcareer.catalog.alias.ClubAliasSeedLoader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** club_alias tohumu: hash dosyası içerikle uyuşur; yükleyici açılışta tablo üzerinde hata vermeden çalışır; bilinmeyen alias bulunmaz. */
@IntegrationTest
class ClubAliasSeedTest {

    @Autowired
    private ClubAliasRepository aliases;

    @Test
    void theHashFileMatchesTheSeedContent() throws Exception {
        String expected = new String(new ClassPathResource("seed/club-aliases.hash").getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        assertThat(ClubAliasSeedLoader.seedHash()).isEqualTo(expected);
    }

    @Test
    void anUnknownNameHasNoAlias() {
        assertThat(aliases.findClubId("Hiç Yok FC", null, 0)).isEmpty();
    }
}
