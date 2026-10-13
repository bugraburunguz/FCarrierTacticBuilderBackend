package com.fcareer.hardening;

import com.fcareer.common.configuration.LimitsProperties;
import com.fcareer.common.configuration.RequestBodyLimitFilter;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class RequestBodyLimitFilterTest {

    private final LimitsProperties limits = limits();
    private final RequestBodyLimitFilter filter = new RequestBodyLimitFilter(limits);

    private static LimitsProperties limits() {
        LimitsProperties p = new LimitsProperties();
        p.setJsonBytes(1_000);
        p.setCaptureBytes(5_000);
        p.setMultipartBytes(2_000);
        return p;
    }

    @Test
    void multipartUsesItsOwnBudgetByContentLength() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/careers/1/import");
        request.setContentType("multipart/form-data; boundary=x");
        request.setContent(new byte[3_000]);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(413);
    }

    @Test
    void bodyWithoutContentLengthIsCountedWhileRead() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/ut/capture");
        request.setContent(new byte[6_000]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) throws IOException, ServletException {
                req.getInputStream().readAllBytes();
            }
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
    }

    @Test
    void bodyWithinBudgetPasses() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/ut/capture");
        request.setContent(new byte[4_000]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        int[] seen = new int[1];
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) throws IOException {
                seen[0] = req.getInputStream().readAllBytes().length;
            }
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(seen[0]).isEqualTo(4_000);
    }
}
