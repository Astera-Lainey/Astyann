package com.astyann.gateway.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Intercepts exceptions that escape the filter chain and returns a
 * consistent JSON payload. Ordered at -1 to run before Spring Boot's
 * default error handler.
 *
 * This is needed because Spring Cloud Gateway runs on Netty (reactive),
 * so the servlet-world @ControllerAdvice does not apply here.
 */
@Component
@Order(-1)
@Slf4j
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper mapper;

    public GatewayExceptionHandler() {
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        // Map exception type to HTTP status
        HttpStatus status = resolveStatus(ex);
        String     path   = exchange.getRequest().getURI().getPath();

        log.warn("Gateway error [{}] on {}: {}", status, path, ex.getMessage());

        GatewayErrorResponse body = new GatewayErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                ex.getMessage(),
                path,
                LocalDateTime.now()
        );

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[]     bytes  = toBytes(body);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private HttpStatus resolveStatus(Throwable ex) {
        String name = ex.getClass().getSimpleName();
        return switch (name) {
            case "UnauthorizedException"   -> HttpStatus.UNAUTHORIZED;
            case "ForbiddenException"      -> HttpStatus.FORBIDDEN;
            case "ExpiredJwtException"     -> HttpStatus.UNAUTHORIZED;
            case "MalformedJwtException",
                 "SignatureException",
                 "UnsupportedJwtException" -> HttpStatus.UNAUTHORIZED;
            default                        -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private byte[] toBytes(Object obj) {
        try {
            return mapper.writeValueAsBytes(obj);
        } catch (JsonProcessingException e) {
            return "{}".getBytes();
        }
    }
}
