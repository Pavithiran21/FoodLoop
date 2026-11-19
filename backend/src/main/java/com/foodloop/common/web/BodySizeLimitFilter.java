package com.foodloop.common.web;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Refuses request bodies over 10 KB (every FoodLoop request is a small JSON object), except the listing photo upload,
 * which may be 5 MB plus the multipart wrapper. A refused body is read and thrown away (up to a cap) before the answer, so
 * the 413 reaches the browser (and the Vite proxy) instead of a reset connection.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class BodySizeLimitFilter extends OncePerRequestFilter {

    private static final long MAX_BODY_BYTES = 10 * 1024;
    private static final long MAX_PHOTO_REQUEST_BYTES = 6 * 1024 * 1024;
    private static final long MAX_DRAIN_BYTES = 16 * 1024 * 1024;

    private final ErrorWriter errorWriter;

    public BodySizeLimitFilter(ErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean photoUpload = "PUT".equals(request.getMethod()) && request.getRequestURI().matches("/api/donor/listings/\\d+/photo");
        long limit = photoUpload ? MAX_PHOTO_REQUEST_BYTES : MAX_BODY_BYTES;
        if (request.getContentLengthLong() > limit) {
            drain(request);
            String message = photoUpload ? "That file can't be used. Use a JPG or PNG photo up to 5 MB." : "The request body is too large.";
            errorWriter.write(request, response, 413, "PAYLOAD_TOO_LARGE", message);
            return;
        }
        chain.doFilter(request, response);
    }

    private static void drain(HttpServletRequest request) {
        try (var in = request.getInputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while (total < MAX_DRAIN_BYTES && (read = in.read(buffer)) != -1) {
                total += read;
            }
        } catch (IOException ignored) {
            // The client went away while we were reading: nothing more to do.
        }
    }
}
