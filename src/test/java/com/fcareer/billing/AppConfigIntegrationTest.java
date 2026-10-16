package com.fcareer.billing;

import com.fcareer.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PROD-PREP 2: arayüzün beta etiketini ve günlük limiti tek kaynaktan okuduğu herkese açık uç. Kodda beta kapalı, limit 30. */
@IntegrationTest
class AppConfigIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousCanReadBetaFlagAndDailyCredits() throws Exception {
        mockMvc.perform(get("/api/v1/config/app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.freeBeta").value(false))
                .andExpect(jsonPath("$.data.dailyCredits").value(30));
    }
}
