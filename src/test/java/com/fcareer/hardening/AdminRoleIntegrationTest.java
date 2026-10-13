package com.fcareer.hardening;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@IntegrationTest
@TestPropertySource(properties = "fcareer.auth.bootstrap-admin-email=Boot-Admin@Example.com")
class AdminRoleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;

    private String register(String email) throws Exception {
        return call(post("/api/v1/auth/register"), Map.of("email", email, "password", "Sup3rSecret!"), null, 200).at("/data/accessToken").asText();
    }

    private String login(String email) throws Exception {
        return call(post("/api/v1/auth/login"), Map.of("email", email, "password", "Sup3rSecret!"), null, 200).at("/data/accessToken").asText();
    }

    @Test
    void adminEndpointsNeedTheAdminRoleNotTheSharedKey() throws Exception {
        String userEmail = "plain-" + UUID.randomUUID() + "@example.com";
        String userToken = register(userEmail);
        call(get("/api/v1/admin/ut/queue"), null, null, 401);
        call(get("/api/v1/admin/ut/queue"), null, userToken, 403);
        call(get("/api/v1/admin/ut/queue").header("X-Admin-Key", "test-key"), null, null, 403);

        String adminEmail = "admin-" + UUID.randomUUID() + "@example.com";
        register(adminEmail);
        jdbc.update("UPDATE app_user SET role = 'ADMIN' WHERE email = ?", adminEmail);
        String adminToken = login(adminEmail);

        call(get("/api/v1/admin/ut/queue"), null, adminToken, 200);
        assertThat(call(get("/api/v1/me"), null, adminToken, 200).at("/data/role").asText()).isEqualTo("ADMIN");
        assertThat(call(get("/api/v1/me"), null, userToken, 200).at("/data/role").asText()).isEqualTo("USER");
    }

    @Test
    void theKeyStillWorksForMachineToMachineIngestionOnly() throws Exception {
        call(post("/api/v1/admin/ingestion/run"), null, null, 401);
        call(post("/api/v1/admin/ingestion/run").header("X-Admin-Key", "test-key"), null, null, 200);
    }

    @Test
    void auditRowsCarryTheAdminUserId() throws Exception {
        String adminEmail = "audit-" + UUID.randomUUID() + "@example.com";
        register(adminEmail);
        jdbc.update("UPDATE app_user SET role = 'ADMIN' WHERE email = ?", adminEmail);
        long adminId = jdbc.queryForObject("SELECT id FROM app_user WHERE email = ?", Long.class, adminEmail);
        String adminToken = login(adminEmail);
        String target = "target-" + UUID.randomUUID() + "@example.com";
        register(target);
        long targetId = jdbc.queryForObject("SELECT id FROM app_user WHERE email = ?", Long.class, target);

        call(post("/api/v1/admin/ut/contributors/" + targetId + "/grant"), null, adminToken, 200);

        assertThat(jdbc.queryForObject("SELECT actor FROM contributor_audit WHERE user_id = ? ORDER BY id DESC LIMIT 1", String.class, targetId)).isEqualTo("user:" + adminId);
    }

    @Test
    void theBootstrapEmailBecomesAdminAtRegistration() throws Exception {
        jdbc.update("DELETE FROM app_user WHERE email = ?", "boot-admin@example.com");
        String token = register("Boot-Admin@Example.com");

        assertThat(call(get("/api/v1/me"), null, token, 200).at("/data/role").asText()).isEqualTo("ADMIN");
        call(get("/api/v1/admin/ut/queue"), null, token, 200);
    }

    /** manual-test-1 yerel testte SQL ile ADMIN yapılmıştı (kod yolu değil); bootstrap yalnız yapılandırılmış e-postayı yükseltir. */
    @Test
    void onlyTheConfiguredEmailBecomesAdminNoOtherRegistrationPath() throws Exception {
        jdbc.update("DELETE FROM app_user WHERE email IN (?, ?)", "manual-test-1@example.com", "someone-else@example.com");
        String other = register("manual-test-1@example.com");
        String plain = register("someone-else@example.com");

        assertThat(call(get("/api/v1/me"), null, other, 200).at("/data/role").asText()).isEqualTo("USER");
        assertThat(call(get("/api/v1/me"), null, plain, 200).at("/data/role").asText()).isEqualTo("USER");
        call(get("/api/v1/admin/ut/queue"), null, other, 403);
    }

    private JsonNode call(MockHttpServletRequestBuilder builder, Object body, String bearer, int expectedStatus) throws Exception {
        builder.accept(MediaType.APPLICATION_JSON);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body));
        }
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        var response = mockMvc.perform(builder).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(expectedStatus);
        return response.getContentAsString().isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(response.getContentAsString());
    }
}
