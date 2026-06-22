package com.astyann.gateway.config;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Predicate;

/**
 * Defines which routes are PUBLIC (no JWT required) and which routes
 * require a specific role beyond basic authentication.
 *
 * Centralising this here means the JwtAuthenticationFilter stays clean
 * and route policy changes are made in one place.
 */
@Component
public class RouteValidator {

    // ── Public endpoints — JWT is NOT required ────────────────────────────
    public static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/verify",
            "/api/v1/auth/verify/resend",
            "/api/v1/auth/reset-password",
            "/actuator/health",
            "/actuator/info"
    );

    // ── Admin-only endpoints — requires ROLE_ADMIN ────────────────────────
    public static final List<String> ADMIN_ONLY_PATHS = List.of(
            "/api/v1/admin"          // prefix — all /admin/** routes
    );

    // ── Predicates ────────────────────────────────────────────────────────

    /** True when the request does NOT need a JWT token. */
    public Predicate<ServerHttpRequest> isPublic() {
        return request -> PUBLIC_PATHS.stream()
                .anyMatch(path -> request.getURI().getPath().equals(path));
    }

    /** True when the endpoint requires ROLE_ADMIN specifically. */
    public Predicate<ServerHttpRequest> isAdminOnly() {
        return request -> ADMIN_ONLY_PATHS.stream()
                .anyMatch(path -> request.getURI().getPath().startsWith(path));
    }
}
