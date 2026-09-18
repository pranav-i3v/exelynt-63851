package com.exelynt.booking.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Emits exactly one access-log line per request: method, path, status, duration.
 *
 * <p>Deliberately never touches headers, the request body or query values, so
 * credentials, tokens and the {@code Authorization} header cannot reach the log.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        String failure = null;
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException ex) {
            // The status on the response is still the default at this point; the
            // container only sets 5xx during the error dispatch that follows. Logging
            // it as-is would record a failed request as a 200.
            failure = ex.getClass().getSimpleName();
            throw ex;
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
            if (failure == null) {
                log.info("http_request method={} path={} status={} durationMs={}",
                        request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs);
            } else {
                log.warn("http_request method={} path={} status=failed error={} durationMs={}",
                        request.getMethod(), request.getRequestURI(), failure, durationMs);
            }
        }
    }
}
