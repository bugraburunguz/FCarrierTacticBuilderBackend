package com.fcareer.hardening;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DEPLOY-01 1–2: application.yml Railway değişkenlerinden (PORT, PGHOST/PGPORT/PGDATABASE/PGUSER/PGPASSWORD, REDIS_URL) bağlanır;
 * yerel compose değişkenleri (DB_URL, DB_USERNAME, DB_PASSWORD, REDIS_HOST, SERVER_PORT) önceliklidir ve varsayılanlar aynı kalır.
 */
class RailwayConfigResolutionTest {

    private static StandardEnvironment environment(Map<String, Object> variables) throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource("env", variables));
        new YamlPropertySourceLoader().load("app", new ClassPathResource("application.yml")).forEach(environment.getPropertySources()::addLast);
        return environment;
    }

    @Test
    void railwayVariablesBuildTheConnections() throws Exception {
        StandardEnvironment env = environment(Map.of("PORT", "7777", "PGHOST", "postgres.railway.internal", "PGPORT", "5432", "PGDATABASE", "railway",
                "PGUSER", "postgres", "PGPASSWORD", "pw", "REDIS_URL", "redis://default:pw@redis.railway.internal:6379"));
        assertThat(env.getProperty("server.port")).isEqualTo("7777");
        assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://postgres.railway.internal:5432/railway");
        assertThat(env.getProperty("spring.datasource.username")).isEqualTo("postgres");
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("pw");
        assertThat(env.getProperty("spring.data.redis.url")).isEqualTo("redis://default:pw@redis.railway.internal:6379");
    }

    @Test
    void localComposeVariablesAndDefaultsAreUnchanged() throws Exception {
        StandardEnvironment compose = environment(Map.of("DB_URL", "jdbc:postgresql://postgres:5432/fcareer", "DB_USERNAME", "fcareer", "DB_PASSWORD", "x",
                "REDIS_HOST", "redis", "PGHOST", "ignored"));
        assertThat(compose.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://postgres:5432/fcareer");
        assertThat(compose.getProperty("spring.datasource.username")).isEqualTo("fcareer");
        assertThat(compose.getProperty("spring.data.redis.url")).isEqualTo("redis://redis:6379");
        assertThat(compose.getProperty("server.port")).isEqualTo("8080");

        StandardEnvironment bare = environment(Map.of());
        assertThat(bare.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://localhost:5432/fcareer");
        assertThat(bare.getProperty("spring.datasource.username")).isEqualTo("fcareer");
        assertThat(bare.getProperty("spring.data.redis.url")).isEqualTo("redis://localhost:6379");
    }
}
