package com.fcareer.ut.kb;

import com.fcareer.common.exception.DownstreamServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisLinkTokenStore implements LinkTokenStore {

    private static final String PREFIX = "fc:ut:link:";
    private static final String USER_PREFIX = "fc:ut:link-user:";
    private static final int TOKEN_BYTES = 32;

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom random = new SecureRandom();

    @Override
    public String issue(Long userId, String mode, Duration ttl) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        try {
            redisTemplate.opsForValue().set(PREFIX + token, userId + ":" + mode, ttl);
            redisTemplate.opsForSet().add(USER_PREFIX + userId, token);
            redisTemplate.expire(USER_PREFIX + userId, ttl);
        } catch (DataAccessException e) {
            log.error("Link token could not be stored userId : {}", userId, e);
            throw new DownstreamServiceException("redis");
        }
        return token;
    }

    @Override
    public Optional<LinkGrant> resolve(String token) {
        try {
            String value = redisTemplate.opsForValue().get(PREFIX + token);
            if (Objects.isNull(value)) {
                return Optional.empty();
            }
            int split = value.indexOf(':');
            return Optional.of(new LinkGrant(Long.valueOf(value.substring(0, split)), value.substring(split + 1)));
        } catch (DataAccessException e) {
            log.warn("Link token lookup failed reason : {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void revokeAll(Long userId) {
        try {
            Set<String> tokens = redisTemplate.opsForSet().members(USER_PREFIX + userId);
            if (Objects.nonNull(tokens)) {
                tokens.forEach(token -> redisTemplate.delete(PREFIX + token));
            }
            redisTemplate.delete(USER_PREFIX + userId);
        } catch (DataAccessException e) {
            log.error("Link tokens could not be revoked userId : {}", userId, e);
            throw new DownstreamServiceException("redis");
        }
    }
}
