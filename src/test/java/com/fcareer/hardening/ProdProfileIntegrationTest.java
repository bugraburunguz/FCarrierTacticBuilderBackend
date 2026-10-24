package com.fcareer.hardening;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * PROD-PREP 6b/6c: Spring prod profili. CORS yalnız WEB_BASE_URL; Actuator yalnız ayrıntısız health; hata yanıtında yığın izi yok;
 * e-posta doğrulaması zorunlu (kayıtta emailVerified=false); loglarda e-posta, şifre ve token yok.
 */
@IntegrationTest
@ActiveProfiles("prod")
@TestPropertySource(properties = {"WEB_BASE_URL=https://fc-assist.example.test", "fcareer.billing.stub-enabled=false", "FCAREER_TRUST_PROXY=true",
        // Sahte SMTP: JavaMailSender mock'lanır, ağa hiçbir şey gitmez (6c: yerelde gerçek gönderim yok).
        "MAIL_HOST=smtp.fake.test", "MAIL_USERNAME=fake", "MAIL_PASSWORD=fake", "MAIL_FROM=noreply@fc-assist.example.test"})
@ExtendWith(OutputCaptureExtension.class)
class ProdProfileIntegrationTest {

    private static final String PASSWORD = "Pr0d-Profil-Sifre!";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private JavaMailSender mailSender;

    @Test
    void corsAllowsOnlyTheWebBaseUrl() throws Exception {
        var allowed = mockMvc.perform(options("/api/v1/roles").header(HttpHeaders.ORIGIN, "https://fc-assist.example.test")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")).andReturn().getResponse();
        assertThat(allowed.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo("https://fc-assist.example.test");

        var other = mockMvc.perform(options("/api/v1/roles").header(HttpHeaders.ORIGIN, "https://evil.example.test")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")).andReturn().getResponse();
        assertThat(other.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
        assertThat(other.getStatus()).isEqualTo(403);
    }

    @Test
    void actuatorExposesOnlyHealthWithoutDetails() throws Exception {
        var health = mockMvc.perform(get("/actuator/health")).andReturn().getResponse();
        assertThat(health.getStatus()).isEqualTo(200);
        assertThat(objectMapper.readTree(health.getContentAsString()).fieldNames()).toIterable().containsExactly("status");
        assertThat(mockMvc.perform(get("/actuator/info")).andReturn().getResponse().getStatus()).isIn(401, 403, 404);
        assertThat(mockMvc.perform(get("/actuator/env")).andReturn().getResponse().getStatus()).isIn(401, 403, 404);
    }

    @Test
    void errorResponsesCarryNoStackTraceOrExceptionDetails() throws Exception {
        var malformed = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{bozuk")).andReturn().getResponse();
        var missing = mockMvc.perform(get("/api/v1/bu-yol-yok")).andReturn().getResponse();
        var error = mockMvc.perform(get("/error")).andReturn().getResponse();
        for (var response : new org.springframework.mock.web.MockHttpServletResponse[] {malformed, missing, error}) {
            String body = response.getContentAsString();
            assertThat(body).doesNotContain("\"trace\"", "\tat ", "at com.fcareer", "Exception:", "\"exception\"");
        }
    }

    @Test
    void registrationRequiresEmailVerificationAndLogsNoPersonalData(CapturedOutput output) throws Exception {
        String email = "prod-" + UUID.randomUUID() + "@example.com";
        var registered = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("email", email, "password", PASSWORD, "privacyVersion", 1)))).andReturn().getResponse();
        assertThat(registered.getStatus()).as(registered.getContentAsString()).isEqualTo(200);
        var session = objectMapper.readTree(registered.getContentAsString()).at("/data");
        String access = session.at("/accessToken").asText();
        String refresh = session.at("/refreshToken").asText();

        var me = objectMapper.readTree(mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access)).andReturn().getResponse().getContentAsString());
        assertThat(me.at("/data/emailVerificationRequired").asBoolean()).isTrue();
        assertThat(me.at("/data/emailVerified").asBoolean()).isFalse();
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        assertThat(sent.getValue().getTo()).containsExactly(email);
        assertThat(sent.getValue().getFrom()).isEqualTo("noreply@fc-assist.example.test");
        assertThat(sent.getValue().getText()).contains("https://fc-assist.example.test/verify-email?token=");

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("email", email, "password", "yanlis-sifre-1")))).andReturn();
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("refreshToken", refresh)))).andReturn();
        mockMvc.perform(post("/api/v1/me/delete").header(HttpHeaders.AUTHORIZATION, "Bearer " + access).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("password", PASSWORD, "emailConfirm", email)))).andReturn();

        assertThat(output.getAll()).doesNotContain(email, PASSWORD, "yanlis-sifre-1", access, refresh);
    }
}
