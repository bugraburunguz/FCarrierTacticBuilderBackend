package com.fcareer.hardening;

import com.fcareer.common.redis.RedisCounter;
import com.fcareer.ingestion.configuration.IngestionProperties;
import com.fcareer.ingestion.security.AdminKeyFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminKeyFilterTest {

    private final RedisCounter counter = mock(RedisCounter.class);
    private final IngestionProperties properties = new IngestionProperties();
    private final AdminKeyFilter filter;

    AdminKeyFilterTest() {
        properties.setAdminKey("secret-key");
        filter = new AdminKeyFilter(properties, counter, false);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse run(String path, String key) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        if (key != null) {
            request.addHeader("X-Admin-Key", key);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void keyIsAcceptedOnlyOnMachineToMachinePaths() throws Exception {
        assertThat(run("/api/v1/admin/ingestion/run", "secret-key").getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities()).extracting(Object::toString).contains("ROLE_ADMIN");
        SecurityContextHolder.clearContext();

        assertThat(run("/api/v1/admin/ut/queue", "secret-key").getStatus()).isEqualTo(403);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void withoutAKeyTheRequestIsPassedOnToTheRoleCheck() throws Exception {
        assertThat(run("/api/v1/admin/ut/queue", null).getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void lockoutStoreOutageFailsClosedAndWrongKeysAreRejected() throws Exception {
        assertThat(run("/api/v1/admin/ingestion/run", "wrong").getStatus()).isEqualTo(403);

        when(counter.current(org.mockito.ArgumentMatchers.anyString())).thenThrow(new RedisConnectionFailureException("down"));
        assertThat(run("/api/v1/admin/ingestion/run", "secret-key").getStatus()).isEqualTo(429);
    }
}
