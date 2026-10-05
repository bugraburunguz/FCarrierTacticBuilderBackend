package com.fcareer.auth.security;

import java.time.Duration;
import java.util.Optional;

public interface RefreshTokenStore {

    void save(String tokenHash, Long userId, Duration ttl);

    Optional<Long> consume(String tokenHash);
}
