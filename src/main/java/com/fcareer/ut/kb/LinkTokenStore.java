package com.fcareer.ut.kb;

import java.time.Duration;
import java.util.Optional;

/** Extension bağlama oturumu (EXT-01): JWT extension'a verilmez; kısa ömürlü, kullanıcıya bağlı, çok parçalı gönderime izin veren oturum anahtarı. */
public interface LinkTokenStore {

    record LinkGrant(Long userId, String mode) {
    }

    String issue(Long userId, String mode, Duration ttl);

    Optional<LinkGrant> resolve(String token);
}
