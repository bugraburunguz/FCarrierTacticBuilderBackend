package com.fcareer.auth.mail;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Yalnız dev profilinde: token'ı gelen kutusundan görmenin tek yolu. */
@Profile("dev")
@RestController
@RequestMapping("/dev/mail")
@ConditionalOnBean(OutboxVerificationMailer.class)
@RequiredArgsConstructor
public class DevMailController {

    private final OutboxVerificationMailer outbox;

    @GetMapping("/outbox")
    public List<OutboxVerificationMailer.Message> outbox() {
        return outbox.messages();
    }
}
