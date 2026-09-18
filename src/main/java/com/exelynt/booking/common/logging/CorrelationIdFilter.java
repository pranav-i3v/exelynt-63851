package com.exelynt.booking.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * First filter in the chain. It takes the inbound {@code X-Correlation-Id} (or
 * mints one), publishes it to the MDC so every log line of the request carries
 * it, echoes it back on the response and always clears the MDC afterwards.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private static final int MAX_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = sanitize(request.getHeader(HEADER_NAME));
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER_NAME, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** Returns the correlation id of the current request, or an empty string outside one. */
    public static String current() {
        String value = MDC.get(MDC_KEY);
        return value == null ? "" : value;
    }

    /**
     * Client-supplied ids are echoed into a response header and into logs, so
     * anything outside a conservative character set is replaced by a fresh UUID
     * rather than trusted.
     */
    private static String sanitize(String candidate) {
        if (candidate == null || candidate.isBlank() || candidate.length() > MAX_LENGTH) {
            return UUID.randomUUID().toString();
        }
        String trimmed = candidate.trim();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean allowed = Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.';
            if (!allowed) {
                return UUID.randomUUID().toString();
            }
        }
        return trimmed;
    }
}
