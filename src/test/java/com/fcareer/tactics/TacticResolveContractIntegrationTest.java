package com.fcareer.tactics;

import com.fcareer.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** V3-00 sözleşmesi: formasyonsuz çözme isteği 400 (istemci formasyon hazır olmadan göndermemeli), formasyonlu istek 200. */
@IntegrationTest
class TacticResolveContractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private int status(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/tactics/resolve").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();
    }

    @Test
    void resolvingWithoutAFormationIsA400AndWithOneIsA200() throws Exception {
        assertThat(status("{\"slots\":[],\"setup\":{\"buildUp\":\"Balanced\",\"depth\":55}}")).isEqualTo(400);
        assertThat(status("{\"formation\":\"4-2-3-1 (2)\",\"slots\":[],\"setup\":{\"buildUp\":\"Balanced\",\"depth\":55}}")).isEqualTo(200);
    }
}
