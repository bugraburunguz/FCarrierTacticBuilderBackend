package com.fcareer.auth.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * İstemci IP'si (PROD-PREP 6a): hız sınırı, kayıt sınırı ve yönetici anahtarı deneme sayacı için.
 * Zincir: istemci → Railway edge (X-Real-IP; docs.railway.com/networking/public-networking/specs-and-limits) → web nginx (aynen iletir) → backend.
 * Başlık yalnız trustProxy (FCAREER_TRUST_PROXY=true) iken ve tek, geçerli bir adresse okunur; X-Forwarded-For hiç okunmaz (istemci ekleyebilir).
 * Güven kapalıyken bağlantı adresi kullanılır.
 */
public final class ClientIp {

    public static final String HEADER = "X-Real-IP";
    private static final Pattern ADDRESS = Pattern.compile("^(?:\\d{1,3}(?:\\.\\d{1,3}){3}|[0-9a-fA-F:]{2,39}(?::\\d{1,3}(?:\\.\\d{1,3}){3})?)$");

    private ClientIp() {
    }

    public static String of(HttpServletRequest request, boolean trustProxy) {
        if (!trustProxy) {
            return request.getRemoteAddr();
        }
        String header = request.getHeader(HEADER);
        if (Objects.isNull(header) || !ADDRESS.matcher(header.trim()).matches()) {
            return request.getRemoteAddr();
        }
        return header.trim();
    }
}
