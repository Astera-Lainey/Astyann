package afb.astyann.authservice.security;

import afb.astyann.authservice.domain.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Component
@Slf4j
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.access-expiration-ms}")
    private long accessExpiration;

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshExpiration;

    // ── Token Generation ────────────────────────────────────────────────────

    public String generateAccessToken(User user) {
        return buildToken(user, accessExpiration, "access");
    }

    public String generateRefreshToken(User user) {
        return buildToken(user, refreshExpiration, "refresh");
    }

    private String buildToken(User user, long expiration, String tokenType) {
        return Jwts.builder()
                .subject(user.getUserId().toString())
                .claim("email", user.getEmail())
                .claim("type",  tokenType)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    // ── Token Parsing ───────────────────────────────────────────────────────

    public UUID extractUserId(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    public String extractEmail(String token) {
        return (String) parseClaims(token).get("email");
    }

    public boolean isTokenValid(String token, User user) {
        try {
            Claims claims = parseClaims(token);
            String subject = claims.getSubject();
            return subject.equals(user.getUserId().toString())
                    && !isTokenExpired(claims);
        } catch (JwtException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private boolean isTokenExpired(Claims claims) {
        return claims.getExpiration().before(new Date());
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
