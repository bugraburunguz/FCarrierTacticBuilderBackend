package com.fcareer.auth.security;

import java.time.Duration;
import java.util.Optional;

public interface RefreshTokenStore {

    void save(String tokenHash, Long userId, Duration ttl);

    /** Token'ı tek kullanımlık tüketir; tüketilen token bir süre "kullanıldı" olarak hatırlanır (yeniden kullanım tespiti, SEC-08). */
    Optional<Long> consume(String tokenHash);

    /** Daha önce tüketilmiş bir token sunulduysa sahibini döner: çalınmış token şüphesi. */
    Optional<Long> usedBy(String tokenHash);

    /** Kullanıcının bütün açık refresh token'larını iptal eder (aile iptali). */
    void revokeAll(Long userId);
}
