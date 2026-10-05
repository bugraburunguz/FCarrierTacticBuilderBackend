package com.fcareer.auth.security;

import com.fcareer.common.exception.DownstreamServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import static com.fcareer.common.constants.RedisConstants.REFRESH_TOKEN_PREFIX;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String REDIS = "redis";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void save(String tokenHash, Long userId, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(REFRESH_TOKEN_PREFIX + tokenHash, String.valueOf(userId), ttl);
        } catch (DataAccessException e) {
            log.error("Refresh token could not be stored userId : {}", userId, e);
            throw new DownstreamServiceException(REDIS);
        }
    }

    @Override
    public Optional<Long> consume(String tokenHash) {
        try {
            String userId = redisTemplate.opsForValue().getAndDelete(REFRESH_TOKEN_PREFIX + tokenHash);
            return Objects.isNull(userId) ? Optional.empty() : Optional.of(Long.valueOf(userId));
        } catch (DataAccessException e) {
            log.error("Refresh token lookup failed", e);
            throw new DownstreamServiceException(REDIS);
        }
    }
}
