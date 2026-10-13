package com.fcareer.auth;

import com.fcareer.auth.configuration.RateLimitProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/** Kayıt hız sınırı config'ten gelir (fcareer.ratelimit.register.*): prod varsayılanı 5, dev profili gate betikleri için geniş. */
class RegisterRateLimitConfigTest {

    private static Properties yaml(String resource) {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource(resource));
        return factory.getObject();
    }

    @Test
    void theProductionDefaultIsFivePerIpPerDay() {
        assertThat(new RateLimitProperties().getRegister().getDailyPerIp()).isEqualTo(5);
        assertThat(yaml("application.yml").getProperty("fcareer.ratelimit.register.daily-per-ip")).contains(":5}");
    }

    @Test
    void theDevProfileRaisesTheLimitForGateScripts() {
        assertThat(Integer.parseInt(yaml("application-dev.yml").getProperty("fcareer.ratelimit.register.daily-per-ip"))).isGreaterThanOrEqualTo(200);
    }

    @Test
    void theBaseProfileNeverCarriesTheDevValue() {
        assertThat(yaml("application.yml").getProperty("fcareer.ratelimit.register.daily-per-ip")).doesNotContain("500").doesNotContain("1000");
    }
}
