package com.fcareer;

import com.fcareer.auth.security.RefreshTokenStore;
import com.fcareer.common.redis.RedisCounter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.fcareer.ut.kb.LinkTokenStore;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@TestConfiguration
public class TestSupportConfiguration {

    @Bean
    @Primary
    public LinkTokenStore inMemoryLinkTokenStore() {
        Map<String, LinkTokenStore.LinkGrant> grants = new ConcurrentHashMap<>();
        return new LinkTokenStore() {
            @Override
            public String issue(Long userId, String mode, Duration ttl) {
                String token = java.util.UUID.randomUUID().toString();
                grants.put(token, new LinkGrant(userId, mode));
                return token;
            }

            @Override
            public Optional<LinkGrant> resolve(String token) {
                return Optional.ofNullable(grants.get(token));
            }
        };
    }

    /** Testlerde Redis yok: sayaç bellek içi (fail-closed kapıların test edilebilmesi için). */
    @Bean
    @Primary
    public RedisCounter inMemoryCounter() {
        Map<String, java.util.concurrent.atomic.AtomicLong> counts = new ConcurrentHashMap<>();
        return new RedisCounter(null) {
            @Override
            public long increment(String key, Duration window) {
                return counts.computeIfAbsent(key, k -> new java.util.concurrent.atomic.AtomicLong()).incrementAndGet();
            }

            @Override
            public long current(String key) {
                java.util.concurrent.atomic.AtomicLong value = counts.get(key);
                return value == null ? 0 : value.get();
            }
        };
    }

    @Bean
    @Primary
    public RefreshTokenStore inMemoryRefreshTokenStore() {
        Map<String, Long> tokens = new ConcurrentHashMap<>();
        Map<String, Long> used = new ConcurrentHashMap<>();
        return new RefreshTokenStore() {
            @Override
            public void save(String tokenHash, Long userId, Duration ttl) {
                tokens.put(tokenHash, userId);
            }

            @Override
            public Optional<Long> consume(String tokenHash) {
                Long userId = tokens.remove(tokenHash);
                if (userId != null) {
                    used.put(tokenHash, userId);
                }
                return Optional.ofNullable(userId);
            }

            @Override
            public Optional<Long> usedBy(String tokenHash) {
                return Optional.ofNullable(used.get(tokenHash));
            }

            @Override
            public void revokeAll(Long userId) {
                tokens.values().removeIf(userId::equals);
            }
        };
    }
}
