package com.fcareer;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.SpringApplication;

import java.nio.file.Path;
import java.util.Map;

/**
 * Yerel geliştirme başlatıcısı (Docker olmadan): kalıcı embedded Postgres + uygulama.
 * Redis yerine bellek-içi refresh store kullanılır (TestSupportConfiguration). Üretimde KULLANILMAZ.
 */
public final class DevApplication {

    private DevApplication() {
    }

    public static void main(String[] args) throws Exception {
        Path dataDir = Path.of(System.getProperty("dev.data-dir", System.getProperty("user.home") + "/tools/devdata"));
        EmbeddedPostgres postgres = EmbeddedPostgres.builder()
                .setDataDirectory(dataDir)
                .setCleanDataDirectory(false)
                .setPort(5433)
                .setLocaleConfig("locale", "C")
                .start();

        Map<String, String> overrides = Map.of(
                "spring.datasource.url", postgres.getJdbcUrl("postgres", "postgres"),
                "spring.datasource.username", "postgres",
                "spring.datasource.password", "",
                "fcareer.security.jwt.secret", "dev-only-secret-dev-only-secret-dev-only-secret",
                "fcareer.security.rate-limit.enabled", "false",
                "fcareer.ingestion.admin-key", "dev-admin-key",
                "fcareer.ingestion.mock.player-count", System.getProperty("dev.players", "600"));
        overrides.forEach(System::setProperty);
        SpringApplication.run(new Class<?>[]{FcareerApplication.class, TestSupportConfiguration.class}, args);
    }
}
