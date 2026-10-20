package com.fcareer.auth;

import com.fcareer.IntegrationTest;
import com.fcareer.auth.security.JdbcRefreshTokenStore;
import com.fcareer.auth.security.RefreshTokenStore;
import com.fcareer.ut.kb.JdbcLinkTokenStore;
import com.fcareer.ut.kb.LinkTokenStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OPS-02: yenileme ve eklenti bağlama token'ları Redis yerine Postgres'te (deploy'da oturumlar düşmesin). Davranış Redis deposuyla aynı:
 * tek kullanımlık tüketim + "kullanıldı" hatırası (yeniden kullanım tespiti), logout silmesi, aile iptali, süre sonu.
 */
@IntegrationTest
class TokenStoresIntegrationTest {

    @Autowired
    private RefreshTokenStore refreshTokens;
    @Autowired
    private LinkTokenStore linkTokens;
    @Autowired
    private JdbcTemplate jdbc;

    private long user(String tag) {
        return jdbc.queryForObject("INSERT INTO app_user (email, password_hash) VALUES (?, 'x') RETURNING id", Long.class,
                tag + "-" + System.nanoTime() + "@test.local");
    }

    @Test
    void refreshTokensAreSingleUseRememberedRevocableAndExpire() {
        assertThat(refreshTokens).isInstanceOf(JdbcRefreshTokenStore.class);
        long userId = user("refresh");

        refreshTokens.save("h1", userId, Duration.ofDays(14));
        assertThat(refreshTokens.consume("h1")).contains(userId);
        assertThat(refreshTokens.consume("h1")).as("tek kullanımlık").isEmpty();
        assertThat(refreshTokens.usedBy("h1")).as("kullanıldı hatırası").contains(userId);

        refreshTokens.save("h2", userId, Duration.ofDays(14));
        assertThat(refreshTokens.delete("h2")).contains(userId);
        assertThat(refreshTokens.usedBy("h2")).as("logout yeniden kullanım sayılmaz").isEmpty();

        refreshTokens.save("h3", userId, Duration.ofDays(14));
        refreshTokens.save("h4", userId, Duration.ofDays(14));
        refreshTokens.revokeAll(userId);
        assertThat(refreshTokens.consume("h3")).isEmpty();
        assertThat(refreshTokens.consume("h4")).isEmpty();
        assertThat(refreshTokens.usedBy("h1")).as("aile iptali kullanıldı hatırasını silmez").contains(userId);

        refreshTokens.save("h5", userId, Duration.ZERO);
        assertThat(refreshTokens.consume("h5")).as("süresi dolmuş").isEmpty();
    }

    @Test
    void linkTokensResolveUntilRevokedAndAreNotStoredInPlainText() {
        assertThat(linkTokens).isInstanceOf(JdbcLinkTokenStore.class);
        long userId = user("link");

        String token = linkTokens.issue(userId, "CAPTURE", Duration.ofHours(1));
        assertThat(linkTokens.resolve(token)).contains(new LinkTokenStore.LinkGrant(userId, "CAPTURE"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ut_link_token WHERE token_hash = ?", Integer.class, token)).as("düz metin saklanmaz").isZero();
        assertThat(linkTokens.resolve("unknown")).isEmpty();

        String expired = linkTokens.issue(userId, "CAPTURE", Duration.ZERO);
        assertThat(linkTokens.resolve(expired)).isEmpty();

        linkTokens.revokeAll(userId);
        assertThat(linkTokens.resolve(token)).isEmpty();
    }

    @Test
    void deletingTheUserRemovesTheirTokens() {
        long userId = user("cascade");
        refreshTokens.save("hc", userId, Duration.ofDays(14));
        linkTokens.issue(userId, "CAPTURE", Duration.ofHours(1));

        jdbc.update("DELETE FROM app_user WHERE id = ?", userId);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_refresh_token WHERE user_id = ?", Integer.class, userId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ut_link_token WHERE user_id = ?", Integer.class, userId)).isZero();
    }
}
