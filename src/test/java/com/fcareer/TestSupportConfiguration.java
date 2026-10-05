package com.fcareer;

import com.fcareer.auth.security.RefreshTokenStore;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@TestConfiguration
public class TestSupportConfiguration {

    @Bean
    @Primary
    public RefreshTokenStore inMemoryRefreshTokenStore() {
        Map<String, Long> tokens = new ConcurrentHashMap<>();
        return new RefreshTokenStore() {
            @Override
            public void save(String tokenHash, Long userId, Duration ttl) {
                tokens.put(tokenHash, userId);
            }

            @Override
            public Optional<Long> consume(String tokenHash) {
                return Optional.ofNullable(tokens.remove(tokenHash));
            }
        };
    }
}
