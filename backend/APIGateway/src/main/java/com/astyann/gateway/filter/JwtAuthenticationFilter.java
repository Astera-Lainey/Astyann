package com.astyann.gateway.filter;

import com.astyann.gateway.config.RouteValidator;
import com.astyann.gateway.exception.ForbiddenException;
import com.astyann.gateway.exception.UnauthorizedException;
import com.astyann.gateway.security.JwtValidator;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Central security filter applied to every route in the gateway.
 *
 * Responsibilities:
 * ─────────────────────────────────────────────────────────────────────────
 * 1. PUBLIC ROUTE BYPASS
 *    Requests matching RouteValidator.PUBLIC_PATHS pass through without
 *    any token requirement.
 *
 * 2. TOKEN PRESENCE CHECK
 *    Protected routes MUST carry an Authorization: Bearer <token> header.
 *    Missing or malformed headers result in 401 Unauthorized.
 *
 * 3. JWT SIGNATURE + EXPIRY VALIDATION
 *    The token is validated locally against the shared HMAC secret.
 *    Expired tokens yield 401; tampered tokens yield 401.
 *    No call is made to the Auth Service at runtime.
 *
 * 4. TOKEN TYPE CHECK
 *    Only access tokens are accepted on business routes.
 *    Refresh tokens are rejected with 401.
 *
 * 5. ROLE-BASED ACCESS CONTROL (RBAC)
 *    Admin-only paths (RouteValidator.ADMIN_ONLY_PATHS) additionally
 *    require the role claim to be ROLE_ADMIN. Any other role yields 403.
 *
 * 6. CLAIM FORWARDING
 *    Once validated, the parsed claims are injected as downstream
 *    request headers so every microservice can trust them without
 *    re-validating the JWT:
 *      X-User-Id    → userId (UUID string)
 *      X-User-Email → email
 *      X-User-Role  → role (ROLE_USER | ROLE_ADMIN)
 *
 *    Microservices MUST reject any of these headers arriving from outside
 *    the gateway (i.e. they should only trust them from internal traffic).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter implements GatewayFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtValidator   jwtValidator;
    private final RouteValidator routeValidator;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // ── Step 1: bypass public routes immediately ──────────────────────
        if (routeValidator.isPublic().test(request)) {
            log.debug("Public route, bypassing auth: {}", path);
            return chain.filter(exchange);
        }

        // ── Step 2: extract Bearer token ─────────────────────────────────
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("Missing or malformed Authorization header on {}", path);
            throw new UnauthorizedException(
                    "Authorization header is missing or does not start with 'Bearer '");
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();

        if (token.isBlank()) {
            throw new UnauthorizedException("JWT token is empty");
        }

        // ── Step 3 & 4: validate signature, expiry, and token type ───────
        Claims claims;
        try {
            claims = jwtValidator.validateAccessToken(token);
        } catch (ExpiredJwtException ex) {
            log.warn("Expired JWT on {}: {}", path, ex.getMessage());
            throw new UnauthorizedException("JWT token has expired. Please log in again.");
        } catch (JwtException ex) {
            log.warn("Invalid JWT on {}: {}", path, ex.getMessage());
            throw new UnauthorizedException("Invalid JWT token: " + ex.getMessage());
        }

        // ── Step 5: role-based access control ────────────────────────────
        String role = jwtValidator.extractRole(claims);

        if (routeValidator.isAdminOnly().test(request)) {
            if (!"ROLE_ADMIN".equals(role)) {
                log.warn("Access denied to admin route {} for role {}", path, role);
                throw new ForbiddenException(
                        "Access denied: admin privileges required for this resource.");
            }
        }

        // ── Step 6: forward identity claims as trusted internal headers ───
        String userId = jwtValidator.extractUserId(claims);
        String email  = jwtValidator.extractEmail(claims);

        ServerHttpRequest mutatedRequest = exchange.getRequest()
                .mutate()
                // Strip the original Authorization header to prevent leaking the
                // raw token to downstream services (they rely on X-User-* headers)
                .headers(headers -> {
                    headers.remove(HttpHeaders.AUTHORIZATION);
                    headers.set("X-User-Id",    userId);
                    headers.set("X-User-Email", email);
                    headers.set("X-User-Role",  role);
                })
                .build();

        log.debug("Auth OK — user={} role={} -> {}", userId, role, path);
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }
}
