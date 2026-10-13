package com.fcareer.hardening;

import com.fcareer.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthContributorRegistry;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** docker healthcheck /actuator/health'e bakar: MAIL_HOST boşken tanımlı mail ayarı posta göstergesini DOWN üretmemeli (gösterge hiç kayıtlı olmamalı). */
@IntegrationTest
@TestPropertySource(properties = {"spring.mail.host=", "spring.mail.port=587"})
class HealthIntegrationTest {

    @Autowired
    private HealthContributorRegistry registry;

    @Test
    void theMailIndicatorDoesNotTakeTheServiceDown() {
        assertThat(registry.getContributor("mail")).isNull();
        assertThat(registry.getContributor("db")).isNotNull();
    }
}
