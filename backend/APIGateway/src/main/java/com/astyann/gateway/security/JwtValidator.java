package com.astyann.gateway.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

/**
 * Stateless JWT validator for the API Gateway.
 *
 * Shares the SAME secret key as the Auth Service and reads the same claims:
 *   sub   -> userId (UUID string)
 *   email -> user email
 *   role  -> ROLE_USER | ROLE_ADMIN
 *   type  -> "access" | "refresh"
 *
 * Validation is purely local — no round-trip to the Auth Service at runtime.
 */
@Component
@Slf4j
public class JwtValidator {

    @Value("${jwt.secret}")
    private String secretKey;

    /**
     * Parses and fully validates a token:
     *  1. HMAC-SHA signature check against the shared secret
     *  2. Expiry enforcement
     *  3. Token type must be "access" — refresh tokens are explicitly rejected
     *
     * @return parsed {@link Claims} ready to be forwarded as headers
     * @throws ExpiredJwtException   when the token has expired
     * @throws MalformedJwtException when token structure is bad or type != "access"
     * @throws JwtException          for any other JWT problem
     */
    public Claims validateAccessToken(String token) {
        Claims claims = parseClaims(token);   // throws ExpiredJwtException / SignatureException etc.

        String tokenType = (String) claims.get("type");
        if (!"access".equals(tokenType)) {
            throw new MalformedJwtException(
                    "Only access tokens are accepted on protected routes. Received type: " + tokenType);
        }

        return claims;
    }

    // ── Claim Extractors ──────────────────────────────────────────────────

    public String extractUserId(Claims claims) {
        return claims.getSubject();           // UUID as string
    }

    public String extractEmail(Claims claims) {
        return (String) claims.get("email");
    }

    public String extractRole(Claims claims) {
        return (String) claims.get("role");   // "ROLE_USER" or "ROLE_ADMIN"
    }

    // ── Internal ──────────────────────────────────────────────────────────

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));
    }
}
