package com.fcareer.common.configuration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

import static com.fcareer.common.constants.ApiConstants.API_V1;

/**
 * /api altındaki her gövde için bayt bütçesi: Content-Length önceden, chunked dahil gövde okunurken sayılarak sınırlanır (BE-SEC-02).
 * Bütçe yol sınıfına göre: capture uçları, multipart yükleme, diğer JSON.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class RequestBodyLimitFilter extends OncePerRequestFilter {

    private static final Set<String> CAPTURE_PATHS = Set.of(API_V1 + "/ut/capture", API_V1 + "/ut/contrib/capture");

    private final LimitsProperties limits;

    /** Sınırı aşan gövde okunurken fırlatılır; advice 413'e çevirir. */
    public static class RequestTooLargeException extends IOException {
        public RequestTooLargeException(long limit) {
            super("request body exceeds " + limit + " bytes");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        long limit = budget(request);
        if (request.getContentLengthLong() > limit) {
            log.warn("Request rejected by Content-Length path : {} bytes : {} limit : {}", request.getRequestURI(), request.getContentLengthLong(), limit);
            reject(response);
            return;
        }
        try {
            chain.doFilter(new CountingRequest(request, limit), response);
        } catch (RequestTooLargeException e) {
            reject(response);
        }
    }

    private long budget(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (CAPTURE_PATHS.contains(request.getRequestURI())) {
            return limits.getCaptureBytes();
        }
        return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/") ? limits.getMultipartBytes() : limits.getJsonBytes();
    }

    private static void reject(HttpServletResponse response) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setContentType("application/json");
        response.getWriter().write("{\"success\":false,\"message\":\"Payload too large\"}");
    }

    private static final class CountingRequest extends HttpServletRequestWrapper {

        private final long limit;
        private ServletInputStream stream;

        CountingRequest(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                stream = new CountingStream(super.getInputStream(), limit);
            }
            return stream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }

    private static final class CountingStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long limit;
        private long read;

        CountingStream(ServletInputStream delegate, long limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        private void count(long n) throws IOException {
            if (n > 0) {
                read += n;
                if (read > limit) {
                    throw new RequestTooLargeException(limit);
                }
            }
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            count(value >= 0 ? 1 : 0);
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int n = delegate.read(buffer, offset, length);
            count(n);
            return n;
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            delegate.setReadListener(listener);
        }
    }
}
