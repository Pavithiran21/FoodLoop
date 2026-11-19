package com.foodloop.demo;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.foodloop.auth.AuthCookies;
import com.foodloop.auth.AuthDtos.AuthResponse;
import com.foodloop.auth.AuthSession;
import com.foodloop.common.web.RateLimiter;
import com.foodloop.common.web.RateLimiter.Limit;
import com.foodloop.demo.DemoService.DemoInfo;
import com.foodloop.user.Role;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Exists only when DEMO_MODE=true; otherwise /api/demo answers 404, which is how the Log in page
 * knows not to show "Try the demo".
 */
@RestController
@RequestMapping("/api/demo")
@ConditionalOnBooleanProperty("app.demo-mode")
public class DemoController {

    private final DemoService demoService;
    private final AuthCookies cookies;
    private final RateLimiter rateLimiter;

    public DemoController(DemoService demoService, AuthCookies cookies, RateLimiter rateLimiter) {
        this.demoService = demoService;
        this.cookies = cookies;
        this.rateLimiter = rateLimiter;
    }

    public record DemoLoginRequest(@NotNull(message = "Pick one of the demo roles.") Role role) {
    }

    @GetMapping
    public DemoInfo info() {
        return demoService.info();
    }

    /** Counts against the same per-IP log-in limit as POST /auth/login. */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody DemoLoginRequest body, HttpServletRequest request) {
        rateLimiter.consume(Limit.LOGIN_IP, request.getRemoteAddr());
        AuthSession session = demoService.login(body.role());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.create(session.refreshToken()))
                .body(new AuthResponse(session.user(), session.accessToken()));
    }
}
