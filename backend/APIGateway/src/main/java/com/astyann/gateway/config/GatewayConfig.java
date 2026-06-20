package com.astyann.gateway.config;

import com.astyann.gateway.filter.JwtAuthenticationFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Programmatic route definitions for the Astyann API Gateway.
 *
 * Route order matters: more specific paths must come before wildcards.
 * Each route:
 *  1. Matches on path prefix
 *  2. Strips the matched prefix (so downstream sees a clean path)
 *  3. Applies the JWT auth filter
 *  4. Forwards to the target service URL
 *
 * All service URLs are injected from application.yml so they can be
 * changed per environment without touching code.
 */
@Configuration
public class GatewayConfig {

    private final JwtAuthenticationFilter jwtFilter;

    public GatewayConfig(JwtAuthenticationFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder) {
        return builder.routes()

            // ── Auth Service (port 8081) — public + authenticated endpoints ──
            .route("auth-service", r -> r
                    .path("/api/v1/auth/**")
                    .filters(f -> f.filter(jwtFilter))
                    .uri("${services.auth-service.url}"))

            // ── Project Service (port 8082) ──────────────────────────────────
            .route("project-service", r -> r
                    .path("/api/v1/projects/**")
                    .filters(f -> f.filter(jwtFilter))
                    .uri("${services.project-service.url}"))

            // ── Requirement Service (port 8083) ──────────────────────────────
            .route("requirement-service", r -> r
                    .path("/api/v1/requirements/**")
                    .filters(f -> f.filter(jwtFilter))
                    .uri("${services.requirement-service.url}"))

            // ── UML Service (port 8084) ───────────────────────────────────────
            .route("uml-service", r -> r
                    .path("/api/v1/uml/**")
                    .filters(f -> f.filter(jwtFilter))
                    .uri("${services.uml-service.url}"))

            // ── Document Service (port 8085) ─────────────────────────────────
            .route("document-service", r -> r
                    .path("/api/v1/documents/**")
                    .filters(f -> f.filter(jwtFilter))
                    .uri("${services.document-service.url}"))

            // ── Code Generation Service (port 8086) ──────────────────────────
            .route("code-generation-service", r -> r
                    .path("/api/v1/code/**")
                    .filters(f -> f.filter(jwtFilter))
                    .uri("${services.code-generation-service.url}"))

            // ── Version Management Service (port 8087) ───────────────────────
            .route("version-management-service", r -> r
                    .path("/api/v1/versions/**")
                    .filters(f -> f.filter(jwtFilter))
                    .uri("${services.version-management-service.url}"))

            // ── Deployment Service (port 8088) ───────────────────────────────
            .route("deployment-service", r -> r
                    .path("/api/v1/deployments/**")
                    .filters(f -> f.filter(jwtFilter))
                    .uri("${services.deployment-service.url}"))

            // ── AI Orchestrator Service (port 8089) ──────────────────────────
            .route("ai-orchestrator-service", r -> r
                    .path("/api/v1/ai/**")
                    .filters(f -> f.filter(jwtFilter))
                    .uri("${services.ai-orchestrator-service.url}"))

            .build();
    }
}
