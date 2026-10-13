package com.fcareer.auth.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** SMTP (MAIL_HOST/MAIL_PORT/MAIL_USERNAME/MAIL_PASSWORD/MAIL_FROM). Gönderim hatası kayıt akışını düşürmez; kullanıcı yeniden gönderebilir. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("!'${spring.mail.host:}'.isEmpty()")
public class SmtpVerificationMailer implements VerificationMailer {

    private final JavaMailSender mailSender;

    @Value("${fcareer.mail.from:no-reply@localhost}")
    private String from;

    @Override
    public void sendVerification(String to, String link, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("FCareer — e-postanı doğrula");
        message.setText("Hesabını etkinleştirmek için bağlantıya tıkla:\n\n" + link + "\n\nBu e-postayı sen istemediysen yok sayabilirsin.");
        try {
            mailSender.send(message);
            log.info("Verification mail sent");
        } catch (RuntimeException e) {
            log.warn("Verification mail could not be sent reason : {}", e.getClass().getSimpleName());
        }
    }
}
