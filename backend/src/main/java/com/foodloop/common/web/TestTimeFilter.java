package com.foodloop.common.web;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.foodloop.common.error.ErrorDetail;
import com.foodloop.config.AppClock;
import com.foodloop.config.AppProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Lets a test say what time it is for one request: "X-Test-Now: 2026-11-12T04:20:00Z". It works only
 * when TIME_OVERRIDE_ENABLED=true, which StartupConfigCheck refuses in production; otherwise the header
 * is ignored. It is the request-level twin of the job endpoint's "at": a test can list food whose pick-up
 * window has already ended, or claim at a chosen moment, at any hour and in parallel, without waiting.
 *
 * Access tokens are still checked against the real time (jjwt uses its own clock).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class TestTimeFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Test-Now";

    private final boolean enabled;
    private final ErrorWriter errorWriter;

    public TestTimeFilter(AppProperties props, ErrorWriter errorWriter) {
        this.enabled = props.timeOverrideEnabled();
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (!enabled || header == null || header.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        Instant now;
        try {
            now = Instant.parse(header.trim());
        } catch (DateTimeParseException ex) {
            errorWriter.write(request, response, 400, "VALIDATION_FAILED", "Some fields are not valid.",
                    List.of(new ErrorDetail(HEADER, "Use a UTC moment like 2026-11-12T04:20:00Z.")));
            return;
        }

        AppClock.setOverride(now);
        try {
            chain.doFilter(request, response);
        } finally {
            AppClock.clearOverride();
        }
    }
}
