package com.fcareer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Testlerde admin JWT'si: kullanıcı kaydedilir, rolü ADMIN yapılır, yeniden giriş yapılır (rol JWT'ye girer). */
public final class AdminTestSupport {

    private static final String PASSWORD = "Sup3rSecret!";

    private AdminTestSupport() {
    }

    public static String adminToken(MockMvc mockMvc, JdbcTemplate jdbc, ObjectMapper objectMapper) throws Exception {
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(Map.of("email", email, "password", PASSWORD, "privacyVersion", 1)))).andReturn();
        jdbc.update("UPDATE app_user SET role = 'ADMIN' WHERE email = ?", email);
        String response = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(Map.of("email", email, "password", PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/accessToken").asText();
    }
}
