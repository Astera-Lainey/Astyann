package com.astyann.gateway.config;

import com.astyann.gateway.filter.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${services.auth-service.url:http://localhost:8081}")
    private String authServiceUrl;

    @Value("${services.project-service.url:http://localhost:8082}")
    private String projectServiceUrl;

    @Value("${services.requirement-service.url:http://localhost:8083}")
    private String requirementServiceUrl;

    @Value("${services.uml-service.url:http://localhost:8084}")
    private String umlServiceUrl;

    @Value("${services.document-service.url:http://localhost:8085}")
    private String documentServiceUrl;

    @Value("${services.code-generation-service.url:http://localhost:8086}")
    private String codeGenerationServiceUrl;

    @Value("${services.version-management-service.url:http://localhost:8087}")
    private String versionManagementServiceUrl;

    @Value("${services.deployment-service.url:http://localhost:8088}")
    private String deploymentServiceUrl;

    @Value("${services.ai-orchestrator-service.url:http://localhost:8089}")
    private String aiOrchestratorServiceUrl;

    @Value("${services.rag-service.url:http://localhost:8090}")
    private String ragServiceUrl;

    public GatewayConfig(JwtAuthenticationFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder) {
        return builder.routes()

            // ── Auth Service (port 8081) — public endpoints only ────────────
            // These endpoints handle their own authentication (login, register,
            // verify-email, forgot-password, etc.). No JWT gateway filter is
            // applied because the client doesn't have a token yet for public
            // endpoints, and refresh-token/logout are validated by the auth
            // service itself.
            .route("auth-service", r -> r
                    .path("/api/v1/auth/**")
                    .uri(authServiceUrl))

            // ── Project Service (port 8082) ──────────────────────────────────
            .route("project-service", r -> r
                    .path("/api/v1/projects/**")
                    .filters(f -> f.filter(jwtFilter))
                    .uri(projectServiceUrl))

            // ── Requirement Service (port 8083) ──────────────────────────────
            // AI inference (PCSF) calls run against large Ollama models with no fixed
            // upper bound — a negative response-timeout disables the gateway timeout.
            .route("requirement-service", r -> r
                    .path("/api/v1/requirements/**")
                    .filters(f -> f.filter(jwtFilter))
                    .metadata("response-timeout", -1L)
                    .uri(requirementServiceUrl))

            // ── UML Service (port 8084) ───────────────────────────────────────
            // Diagram generation runs against large Ollama models with no fixed
            // upper bound — a negative response-timeout disables the gateway timeout.
            .route("uml-service", r -> r
                    .path("/api/v1/uml/**")
                    .filters(f -> f.filter(jwtFilter))
                    .metadata("response-timeout", -1L)
                    .uri(umlServiceUrl))

            // ── Document Service (port 8085) ─────────────────────────────────
            .route("document-service", r -> r
                    .path("/api/v1/documents/**")
                    .filters(f -> f.filter(jwtFilter))
                    .uri(documentServiceUrl))

            // ── Code Generation Service (port 8086) ──────────────────────────
            .route("code-generation-service", r -> r
                    .path("/api/v1/code/**")
                    .filters(f -> f.filter(jwtFilter))
                    .uri(codeGenerationServiceUrl))

            // ── Version Management Service (port 8087) ───────────────────────
            .route("version-management-service", r -> r
                    .path("/api/v1/versions/**")
                    .filters(f -> f.filter(jwtFilter))
                    .uri(versionManagementServiceUrl))

            // ── Deployment Service (port 8088) ───────────────────────────────
            .route("deployment-service", r -> r
                    .path("/api/v1/deployments/**")
                    .filters(f -> f.filter(jwtFilter))
                    .uri(deploymentServiceUrl))

            // ── AI Orchestrator Service (port 8089) ──────────────────────────
            // Inference calls run against large Ollama models with no fixed upper
            // bound — a negative response-timeout disables the gateway timeout.
            .route("ai-orchestrator-service", r -> r
                    .path("/api/v1/ai/**")
                    .filters(f -> f.filter(jwtFilter))
                    .metadata("response-timeout", -1L)
                    .uri(aiOrchestratorServiceUrl))

            // ── RAG Service (port 8090) ───────────────────────────────────────
            .route("rag-service", r -> r
                    .path("/api/v1/rag/**")
                    .filters(f -> f.filter(jwtFilter))
                    .uri(ragServiceUrl))

            .build();
    }
}
