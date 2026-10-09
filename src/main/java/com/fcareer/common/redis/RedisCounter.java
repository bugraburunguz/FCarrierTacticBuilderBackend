package com.fcareer.common.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** Atomik sayaç: INCR ve ilk artışta EXPIRE tek Lua betiğinde çalışır (araya giren çökme anahtarı süresiz bırakamaz). */
@Component
@RequiredArgsConstructor
public class RedisCounter {

    private static final DefaultRedisScript<Long> INCREMENT = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1]) if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end return c", Long.class);

    private final StringRedisTemplate redisTemplate;

    /** Sayacı bir artırıp yeni değeri döner; Redis ulaşılamazsa DataAccessException fırlatır. */
    public long increment(String key, Duration window) {
        Long value = redisTemplate.execute(INCREMENT, List.of(key), String.valueOf(window.toSeconds()));
        return Objects.isNull(value) ? 0 : value;
    }

    /** Sayacı artırmadan okur; yoksa 0. */
    public long current(String key) {
        String value = redisTemplate.opsForValue().get(key);
        return Objects.isNull(value) ? 0 : Long.parseLong(value);
    }
}
