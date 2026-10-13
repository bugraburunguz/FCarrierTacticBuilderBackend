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
import java.util.Set;

import static com.fcareer.common.constants.RedisConstants.REFRESH_TOKEN_PREFIX;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String REDIS = "redis";
    private static final String USED_PREFIX = REFRESH_TOKEN_PREFIX + "used:";
    private static final String USER_PREFIX = REFRESH_TOKEN_PREFIX + "user:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void save(String tokenHash, Long userId, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(REFRESH_TOKEN_PREFIX + tokenHash, String.valueOf(userId), ttl);
            redisTemplate.opsForSet().add(USER_PREFIX + userId, tokenHash);
            redisTemplate.expire(USER_PREFIX + userId, ttl);
        } catch (DataAccessException e) {
            log.error("Refresh token could not be stored userId : {}", userId, e);
            throw new DownstreamServiceException(REDIS);
        }
    }

    @Override
    public Optional<Long> consume(String tokenHash) {
        try {
            String userId = redisTemplate.opsForValue().getAndDelete(REFRESH_TOKEN_PREFIX + tokenHash);
            if (Objects.isNull(userId)) {
                return Optional.empty();
            }
            redisTemplate.opsForSet().remove(USER_PREFIX + userId, tokenHash);
            redisTemplate.opsForValue().set(USED_PREFIX + tokenHash, userId, Duration.ofDays(14));
            return Optional.of(Long.valueOf(userId));
        } catch (DataAccessException e) {
            log.error("Refresh token lookup failed", e);
            throw new DownstreamServiceException(REDIS);
        }
    }

    @Override
    public Optional<Long> delete(String tokenHash) {
        try {
            String userId = redisTemplate.opsForValue().getAndDelete(REFRESH_TOKEN_PREFIX + tokenHash);
            if (Objects.isNull(userId)) {
                return Optional.empty();
            }
            redisTemplate.opsForSet().remove(USER_PREFIX + userId, tokenHash);
            return Optional.of(Long.valueOf(userId));
        } catch (DataAccessException e) {
            log.error("Refresh token delete failed", e);
            throw new DownstreamServiceException(REDIS);
        }
    }

    @Override
    public Optional<Long> usedBy(String tokenHash) {
        try {
            String userId = redisTemplate.opsForValue().get(USED_PREFIX + tokenHash);
            return Objects.isNull(userId) ? Optional.empty() : Optional.of(Long.valueOf(userId));
        } catch (DataAccessException e) {
            log.warn("Refresh reuse lookup failed reason : {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void revokeAll(Long userId) {
        try {
            Set<String> hashes = redisTemplate.opsForSet().members(USER_PREFIX + userId);
            if (Objects.nonNull(hashes)) {
                hashes.forEach(hash -> redisTemplate.delete(REFRESH_TOKEN_PREFIX + hash));
            }
            redisTemplate.delete(USER_PREFIX + userId);
        } catch (DataAccessException e) {
            log.error("Refresh token revoke failed userId : {}", userId, e);
            throw new DownstreamServiceException(REDIS);
        }
    }
}
