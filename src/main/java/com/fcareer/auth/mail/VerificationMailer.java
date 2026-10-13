package com.fcareer.auth.mail;

/** Doğrulama e-postası gönderimi; sağlayıcıdan bağımsız (SMTP ayarları env'den, yalnız env değişince sağlayıcı değişir). */
public interface VerificationMailer {

    /** @param link kullanıcıya gönderilen tam doğrulama bağlantısı; token loglanmaz. */
    void sendVerification(String to, String link, String token);
}
