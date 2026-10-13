package com.fcareer.auth;

import com.fcareer.IntegrationTest;
import com.fcareer.auth.configuration.AuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** docker-compose'un geçirdiği FCAREER_BOOTSTRAP_ADMIN_EMAIL ortam değişkeni gerçekten AuthProperties'e bağlanmalı. */
@IntegrationTest
@TestPropertySource(properties = "FCAREER_BOOTSTRAP_ADMIN_EMAIL=first.admin@example.com")
class BootstrapAdminConfigIntegrationTest {

    @Autowired
    private AuthProperties properties;

    @Test
    void theComposeEnvVariableReachesTheProperty() {
        assertThat(properties.getBootstrapAdminEmail()).isEqualTo("first.admin@example.com");
    }
}
