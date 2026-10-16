package com.fcareer.common;

import com.fcareer.IntegrationTest;
import com.fcareer.common.advice.GlobalExceptionAdvice;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@IntegrationTest
class FrameworkErrorsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private GlobalExceptionAdvice advice;

    private String token() throws Exception {
        String body = "{\"email\":\"fw-" + java.util.UUID.randomUUID() + "@example.com\",\"password\":\"Sup3rSecret!\",\"privacyVersion\":1}";
        String response = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).at("/data/accessToken").asText();
    }

    @Test
    void unknownPathIs404() throws Exception {
        assertThat(mockMvc.perform(get("/api/v1/nope").header("Authorization", "Bearer " + token())).andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void wrongMethodIs405() throws Exception {
        assertThat(mockMvc.perform(post("/api/v1/players").header("Authorization", "Bearer " + token()).contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse().getStatus()).isEqualTo(405);
    }

    @Test
    void unsupportedMediaTypeIs415() throws Exception {
        assertThat(mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.TEXT_PLAIN).content("x")).andReturn().getResponse().getStatus()).isEqualTo(415);
    }

    @Test
    void invalidIdentifiersAndIntegrityViolationsAreClientErrors() {
        assertThat(advice.handleIllegalArgument(new IllegalArgumentException("Bogus")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(advice.handleDataIntegrity(new DataIntegrityViolationException("dup")).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
