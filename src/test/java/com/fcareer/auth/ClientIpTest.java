package com.fcareer.auth;

import com.fcareer.auth.security.ClientIp;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PROD-PREP 6a: istemci IP'si. Yalnız güvenilen tek başlık (X-Real-IP: Railway edge → web nginx) ve yalnız FCAREER_TRUST_PROXY=true iken;
 * X-Forwarded-For hiçbir zaman okunmaz. Geçersiz başlık değeri bağlantı adresine düşer.
 */
class ClientIpTest {

    private static MockHttpServletRequest request(String realIp, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.7");
        if (realIp != null) {
            request.addHeader(ClientIp.HEADER, realIp);
        }
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }

    @Test
    void withoutTrustTheHeadersAreIgnored() {
        assertThat(ClientIp.of(request("203.0.113.5", "198.51.100.1"), false)).isEqualTo("10.0.0.7");
    }

    @Test
    void withTrustOnlyTheRealIpHeaderIsUsed() {
        assertThat(ClientIp.of(request("203.0.113.5", "198.51.100.1"), true)).isEqualTo("203.0.113.5");
        assertThat(ClientIp.of(request("2001:db8::1", null), true)).isEqualTo("2001:db8::1");
        assertThat(ClientIp.of(request(null, "198.51.100.1"), true)).isEqualTo("10.0.0.7");
    }

    @Test
    void malformedHeaderValuesFallBackToTheConnectionAddress() {
        assertThat(ClientIp.of(request("1.2.3.4, 5.6.7.8", null), true)).isEqualTo("10.0.0.7");
        assertThat(ClientIp.of(request("evil:key*", null), true)).isEqualTo("10.0.0.7");
        assertThat(ClientIp.of(request("", null), true)).isEqualTo("10.0.0.7");
    }
}
