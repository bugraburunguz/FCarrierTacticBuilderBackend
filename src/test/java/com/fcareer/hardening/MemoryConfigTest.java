package com.fcareer.hardening;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OPS-RAM: prod belleği (Railway GB-dakika). glibc malloc arena sınırı ve Tomcat thread tavanı; heap tavanı 512 MB'ta kalır. Ölçüm: `Claude outputs/rapor-RAM-analiz.md`, docs/deploy/RAILWAY.md §14.
 */
class MemoryConfigTest {

    @Test
    void tomcatThreadPoolIsCapped() throws Exception {
        StandardEnvironment env = new StandardEnvironment();
        new YamlPropertySourceLoader().load("app", new ClassPathResource("application.yml")).forEach(env.getPropertySources()::addLast);
        assertThat(env.getProperty("server.tomcat.threads.max")).isEqualTo("40");
    }

    @Test
    void dockerImageUsesLowFootprintJvmFlags() throws Exception {
        String dockerfile = Files.readString(Path.of("Dockerfile"));
        // G1 kalır: periyodik GC içe aktarma sonrası heap'i geri verir; Serial GC yalnız tam GC'de küçülür, ~300 MB ayrılmış kalırdı.
        // 512 MB: 384 MB'ta görüntü yazımı açık içe aktarma GC baskısıyla 7 sn → 95–185 sn.
        assertThat(dockerfile).contains("MALLOC_ARENA_MAX=2").contains("-Xmx512m").contains("-XX:+UseG1GC").contains("G1PeriodicGCInterval");
    }
}
