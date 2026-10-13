package com.fcareer.auth.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** SMTP tanımlı değilken kullanılan bellek içi gelen kutusu: yalnız geliştirmede /dev/mail/outbox ile (dev profili) görülür. Token loglanmaz. */
@Slf4j
@Component
@ConditionalOnExpression("'${spring.mail.host:}'.isEmpty()")
public class OutboxVerificationMailer implements VerificationMailer {

    public record Message(String to, String link, String token) {
    }

    private final List<Message> messages = new CopyOnWriteArrayList<>();

    @Override
    public void sendVerification(String to, String link, String token) {
        messages.add(new Message(to, link, token));
        log.info("Verification mail queued to outbox (no SMTP configured)");
    }

    public List<Message> messages() {
        return new ArrayList<>(messages);
    }
}
