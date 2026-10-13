package com.fcareer.auth.service;

import com.fcareer.auth.configuration.AuthProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** İlk admin (BE-SEC-07): FCAREER_BOOTSTRAP_ADMIN_EMAIL'deki hesap açılışta ADMIN olur; kayıt anında da aynı e-posta ADMIN alır. Logda e-posta yazılmaz. */
@Slf4j
@Component
@RequiredArgsConstructor
public class BootstrapAdmin implements ApplicationRunner {

    private final AuthProperties properties;
    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        String email = properties.getBootstrapAdminEmail();
        if (email == null || email.isBlank()) {
            return;
        }
        if (jdbc.update("UPDATE app_user SET role = 'ADMIN' WHERE email = ? AND role <> 'ADMIN'", email.trim().toLowerCase(Locale.ROOT)) > 0) {
            log.info("bootstrap admin atandı");
        }
    }

    public boolean matches(String normalizedEmail) {
        String email = properties.getBootstrapAdminEmail();
        return email != null && !email.isBlank() && email.trim().toLowerCase(Locale.ROOT).equals(normalizedEmail);
    }
}
